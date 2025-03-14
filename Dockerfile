FROM ubuntu:latest

# Set noninteractive mode for apt
ENV DEBIAN_FRONTEND=noninteractive

# Install Java 21 and utilities
RUN apt-get update && apt-get install -y \
    openjdk-21-jdk \
    wget \
    unzip \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Download and extract OpenCV from the Maven JAR
RUN mkdir -p /app/libs \
    && wget -q https://repo1.maven.org/maven2/org/openpnp/opencv/4.9.0-0/opencv-4.9.0-0.jar -O /tmp/opencv.jar \
    && mkdir -p /tmp/opencv \
    && unzip -q /tmp/opencv.jar -d /tmp/opencv \
    && cp -r /tmp/opencv/nu/pattern/opencv/linux/x86_64/* /usr/lib/ \
    && rm -rf /tmp/opencv /tmp/opencv.jar

# Copy application JAR
COPY target/cheating-detection-0.0.1-SNAPSHOT.jar app.jar

# Create required directories
RUN mkdir -p /app/models /app/evidence

# Get GLIBC version for confirmation
RUN ldd --version | grep GLIBC

# Set environment variables
ENV MEDIAPIPE_MODEL_DIRECTORY=/app/models
ENV EVIDENCE_FOLDER=/app/evidence
ENV OPENCV_LOADING_METHOD=auto
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -Xmx512m -Djava.library.path=/usr/lib"
ENV LD_LIBRARY_PATH="/usr/lib"

# Create test script to verify OpenCV libraries
RUN echo '#!/bin/sh' > /app/test-opencv.sh \
    && echo 'echo "Testing OpenCV libraries..."' >> /app/test-opencv.sh \
    && echo 'ls -la /usr/lib/libopencv*' >> /app/test-opencv.sh \
    && echo 'echo "GLIBC version: $(ldd --version | grep GLIBC)"' >> /app/test-opencv.sh \
    && echo 'echo "LD_LIBRARY_PATH: $LD_LIBRARY_PATH"' >> /app/test-opencv.sh \
    && chmod +x /app/test-opencv.sh

# Expose application port
EXPOSE 8081

# Run the test script and then start the application
ENTRYPOINT ["/bin/sh", "-c", "/app/test-opencv.sh && java -jar app.jar"]