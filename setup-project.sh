docker build -t cluster-apache-spark:3.1.2 .
docker-compose up -d --scale spark-worker=2
