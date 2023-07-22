package demo.etl;

import java.util.Properties;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.SaveMode;



/**
 * CSV ingestion in a dataframe.
 * 
 * @author khoale
 */
public class YellowTripDataStagingToAnalytics {

	/**
	 * main() is your entry point to the application.
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		YellowTripDataStagingToAnalytics app = new YellowTripDataStagingToAnalytics();
		app.start();
	}


  /**
   * The processing code.
   */
  private void start() {
		// Creates a session on a local master
		SparkSession spark = SparkSession.builder()
				.appName("NYC Yellow Trip in 2022 - Staging to Analytics")
				.master("spark://spark-master:7077")
				.getOrCreate();


		/* ================ LOADING DATASETS ================ */

		// Reads NYC dataset in 2022
		Dataset<Row> df = spark.read().format("parquet")
				.load("s3a://staging/stg_nyc_yellowtrip_data");

	 /* ================ AGGREGATE DATASET ================ */
	 	Dataset<Row> agg_df = df.groupBy(
			 "month",
			 "day",
			 "vendor_name",
			"rate_code_name",
			"payment_type_name"
			).agg(
				functions.sum("total_amount").alias("total_amount"),
				functions.sum("fare_amount").alias("fare_amount"),
				functions.sum("airport_fee").alias("airport_fee"),
				functions.sum("extra").alias("extra"),
				functions.sum("mta_tax").alias("mta_tax"),
				functions.sum("tip_amount").alias("tip_amount"),
				functions.sum("tolls_amount").alias("tolls_amount"),
				functions.avg("trip_distance").alias("avg_distance_per_trip"),
				functions.count(functions.lit(1)).alias("number_of_records")
			);


	 // Write output to staging folder
	 agg_df.write().partitionBy("month", "vendor_name")
		 .mode("overwrite")
		 .parquet("s3a://analytics/agg_nyc_yellowtrip_data/");

		// Save to postgresql
		String dbConnectionUrl = "jdbc:postgresql://psql-database:5432/spark_labs";

		// Properties to connect to the database, the JDBC driver is part of our
		// pom.xml
		Properties prop = new Properties();
		prop.setProperty("driver", "org.postgresql.Driver");
		prop.setProperty("user", "postgres");
		prop.setProperty("password", "postgres");

		// Write in a table called ch02
		agg_df.write()
			.mode(SaveMode.Overwrite)
			.jdbc(dbConnectionUrl, "yellow_trip", prop);

		spark.stop();
	 	System.out.println("Process complete");
	}
}
