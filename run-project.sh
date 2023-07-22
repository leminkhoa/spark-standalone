export CLASS=demo.etl.YellowTripDataAnalyticsToDatabase
export JAR=deploy-spark-standalone-1.0.0-SNAPSHOT.jar
SPARK_MASTER_ID="$(docker ps -aqf "name=spark-master-1")"
export SPARK_MASTER_ID
export ARGS=''

docker exec -it "$SPARK_MASTER_ID" /opt/spark/bin/spark-submit --class $CLASS --master spark://spark-master:7077 /opt/spark-apps/$JAR $ARGS