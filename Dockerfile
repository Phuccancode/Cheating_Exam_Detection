FROM ubuntu:latest
RUN apt update && apt install -y openjdk-21-jdk
WORKDIR /app

# Create evidence directory for Docker environment
RUN mkdir -p /app/evidence_docker
VOLUME /app/evidence_docker

# Set default evidence folder for Docker
ENV EVIDENCE_FOLDER=evidence_docker

COPY target/cheating-detection-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
