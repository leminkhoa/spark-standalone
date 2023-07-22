FROM openjdk:11.0.11-jre-slim-buster as builder

# Add Dependencies for PySpark
RUN apt-get update && apt-get install -y curl vim wget software-properties-common ssh net-tools ca-certificates

ENV SPARK_VERSION=3.1.2 \
HADOOP_VERSION=3.2 \
SPARK_HOME=/opt/spark \
MVN_VERSION=3.9.3

RUN wget --no-verbose -O apache-spark.tgz "https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz" \
&& mkdir -p /opt/spark \
&& tar -xf apache-spark.tgz -C /opt/spark --strip-components=1 \
&& rm apache-spark.tgz

# Copy project pom file
COPY ./pom.xml $SPARK_HOME/jars

# Install maven
RUN wget --no-verbose -O apache-maven.tgz "https://dlcdn.apache.org/maven/maven-3/${MVN_VERSION}/binaries/apache-maven-${MVN_VERSION}-bin.tar.gz" \
    && mkdir -p /opt/maven \
    && tar -xf apache-maven.tgz -C /opt/maven --strip-components=1 \
    && rm apache-maven.tgz

FROM builder as apache-spark

# Install project dependencies
WORKDIR $SPARK_HOME/jars
RUN /opt/maven/bin/mvn clean install -Dmaven.test.skip=true && \
    /opt/maven/bin/mvn dependency:copy-dependencies -DoutputDirectory=$SPARK_HOME/jars/ && \
    rm -rf ~/.m2/repository/*

# Add dependencies so that it can interact with minio
ADD https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-core/1.11.883/aws-java-sdk-core-1.11.883.jar $SPARK_HOME/jars
ADD https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-dynamodb/1.11.883/aws-java-sdk-dynamodb-1.11.883.jar $SPARK_HOME/jars
ADD https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-s3/1.11.883/aws-java-sdk-s3-1.11.883.jar $SPARK_HOME/jars
ADD https://repo1.maven.org/maven2/org/apache/hadoop/hadoop-aws/3.2.0/hadoop-aws-3.2.0.jar $SPARK_HOME/jars
ADD https://repo1.maven.org/maven2/org/apache/httpcomponents/httpclient/4.5.9/httpclient-4.5.9.jar $SPARK_HOME/jars


WORKDIR /opt/spark

ENV SPARK_MASTER_PORT=7077 \
SPARK_MASTER_WEBUI_PORT=8080 \
SPARK_LOG_DIR=/opt/spark/logs \
SPARK_MASTER_LOG=/opt/spark/logs/spark-master.out \
SPARK_WORKER_LOG=/opt/spark/logs/spark-worker.out \
SPARK_WORKER_WEBUI_PORT=8080 \
SPARK_WORKER_PORT=7000 \
SPARK_MASTER="spark://spark-master:7077" \
SPARK_WORKLOAD="master"

EXPOSE 8080 7077 7000

RUN mkdir -p $SPARK_LOG_DIR && \
touch $SPARK_MASTER_LOG && \
touch $SPARK_WORKER_LOG && \
ln -sf /dev/stdout $SPARK_MASTER_LOG && \
ln -sf /dev/stdout $SPARK_WORKER_LOG

COPY start-spark.sh /

CMD ["/bin/bash", "/start-spark.sh"]