package demo.etl;

import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.functions;

import java.util.Arrays;
import java.util.List;

/**
 * CSV ingestion in a dataframe.
 * 
 * @author khoale
 */
public class YellowTripDataLandingToStaging {

	/**
	 * main() is your entry point to the application.
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		YellowTripDataLandingToStaging app = new YellowTripDataLandingToStaging();
		app.start();
	}


  /**
   * The processing code.
   */
  private void start() {
		// Creates a session on a local master
		SparkSession spark = SparkSession.builder()
				.appName("NYC Yellow Trip in 2022 - Landing to Stanging")
				.master("spark://spark-master:7077")
				.getOrCreate();


		/* ================ LOADING DATASETS ================ */

		// Reads NYC dataset in 2022
		Dataset<Row> df = spark.read().format("parquet")
				.load("s3a://landing/input/2022/");

		// Load Location ID mapping files
		Dataset<Row> location_mapping = spark.read().format("csv")
				.option("header", "true")
				.option("inferSchema", "true")
				.load("s3a://landing/input/taxi+_zone_lookup.csv");

		/* ================ DATA CLEANING ================ */

		// Join with location mapping to get location information

		df = df.join(location_mapping,
							df.col("PULocationID").equalTo(location_mapping.col("LocationID")),
			"left")
				.withColumnRenamed("LocationID", "start_location_id")
				.withColumnRenamed("Borough", "start_borough")
				.withColumnRenamed("Zone", "start_zone")
				.withColumnRenamed("service_zone", "start_service_zone");

	 	df = df.join(location_mapping,
						df.col("DOLocationID").equalTo(location_mapping.col("LocationID")),
			 "left")
			.withColumnRenamed("LocationID", "end_location_id")
			.withColumnRenamed("Borough", "end_borough")
			.withColumnRenamed("Zone", "end_zone")
			.withColumnRenamed("service_zone", "end_service_zone");


		// Decode Vendor ID
		df = df.withColumnRenamed("VendorID", "vendor_id");
		df = df.withColumn("vendor_name", functions
						.when(df.col("vendor_id").equalTo("1"), "Creative Mobile Technologies")
						.when(df.col("vendor_id").equalTo("2"), "VeriFone Inc")
						.otherwise("Unknown"));

		// Decode Rate Code ID
		df = df.withColumnRenamed("RatecodeID", "rate_code_id");

		df = df.withColumn("rate_code_name", functions
			.when(df.col("rate_code_id").equalTo("1"), "Standard Rate")
			.when(df.col("rate_code_id").equalTo("2"), "JFK")
			.when(df.col("rate_code_id").equalTo("3"), "Newark")
			.when(df.col("rate_code_id").equalTo("4"), "Nassau or Westchester")
			.when(df.col("rate_code_id").equalTo("5"), "Negotiated fare")
			.when(df.col("rate_code_id").equalTo("6"), "Group ride")
			.otherwise("Unknown"));

		// Decode Payment Type
		df = df.withColumn("payment_type_name", functions
			.when(df.col("payment_type").equalTo("1"), "Credit Card")
			.when(df.col("payment_type").equalTo("2"), "Cash")
			.when(df.col("payment_type").equalTo("3"), "No charge")
			.when(df.col("payment_type").equalTo("4"), "Dispute")
			.when(df.col("payment_type").equalTo("6"), "Voided trip")
			.otherwise("Unknown"));

		// Add a month and day column
		df = df.withColumn("month", functions.date_format(df.col("tpep_pickup_datetime"), "yyyy-MM"));
		df = df.withColumn("day", functions.date_format(df.col("tpep_pickup_datetime"), "yyyy-MM-dd"));

		// Only keep rows where pickup date in 2022
	 	df = df.filter(functions.year(df.col("tpep_pickup_datetime")).equalTo(2022));

		// Select limit columns
		df = df.select(
			"month",
			"day",
			"vendor_id",
			"vendor_name",
			"tpep_pickup_datetime",
			"tpep_dropoff_datetime",
			"passenger_count",
			"trip_distance",
			"rate_code_id",
			"rate_code_name",
			"start_location_id",
			"start_borough",
			"end_location_id",
			"end_borough",
			"payment_type",
			"payment_type_name",
			"fare_amount",
			"extra",
			"mta_tax",
			"tip_amount",
			"tolls_amount",
			"improvement_surcharge",
			"total_amount",
			"congestion_surcharge",
			"airport_fee"
		);

		// Shows at most 20 rows from the dataframe
		df.show(20, 30);
		System.out.println("The dataset has " + df.count() + " records");

		// Write output to staging folder
	 df.write().partitionBy("month", "vendor_name")
		 .mode("overwrite")
		 .parquet("s3a://staging/stg_nyc_yellowtrip_data/");

	 spark.stop();
	 System.out.println("Staging data has been successfully written!");

	}
}
