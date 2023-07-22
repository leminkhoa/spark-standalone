package demo.etl;

import org.apache.spark.sql.*;

import java.util.Properties;


/**
 * CSV ingestion in a dataframe.
 * 
 * @author khoale
 */
public class YellowTripDataAnalyticsToDatabase {

	/**
	 * main() is your entry point to the application.
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		YellowTripDataAnalyticsToDatabase app = new YellowTripDataAnalyticsToDatabase();
		app.start();
	}


  /**
   * The processing code.
   */
  private void start() {
		// Creates a session on a local master
		SparkSession spark = SparkSession.builder()
				.appName("NYC Yellow Trip in 2022 - Analytics To Database")
				.master("spark://spark-master:7077")
				.getOrCreate();


		/* ================ LOADING DATASETS ================ */

		// Reads NYC dataset in 2022
		Dataset<Row> df = spark.read().format("parquet")
				.load("s3a://analytics/agg_nyc_yellowtrip_data");


		// Save to postgresql
		String dbConnectionUrl = "jdbc:postgresql://psql-database:5432/spark_labs";

		// Properties to connect to the database, the JDBC driver is part of our
		// pom.xml
		Properties prop = new Properties();
		prop.setProperty("driver", "org.postgresql.Driver");
		prop.setProperty("user", "postgres");
		prop.setProperty("password", "postgres");

		// Write in a table called ch02
		df.write()
			.mode(SaveMode.Overwrite)
			.jdbc(dbConnectionUrl, "yellow_trip", prop);

		spark.stop();
		System.out.println("Process complete");
	}
}
