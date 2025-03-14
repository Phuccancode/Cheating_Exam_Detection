FROM amazoncorretto:21.0.6

# Amazon Corretto sử dụng Amazon Linux, nên cần cài đặt OpenCV khác với Ubuntu
RUN yum update -y && yum install -y \
    which \
    findutils \
    tar \
    gzip \
    wget \
    && yum clean all

# Tạo thư mục libs để chứa thư viện OpenCV
RUN mkdir -p /app/libs

# Tải và giải nén thư viện OpenCV từ Maven Central
# Phiên bản này hoạt động tốt với nu.pattern.OpenCV
RUN cd /tmp && \
    wget https://repo1.maven.org/maven2/org/openpnp/opencv/4.9.0-0/opencv-4.9.0-0.jar && \
    mkdir -p /tmp/opencv && \
    cd /tmp/opencv && \
    jar -xf /tmp/opencv-4.9.0-0.jar && \
    cp -r /tmp/opencv/nu/pattern/opencv/linux/x86_64/* /usr/lib64/ && \
    rm -rf /tmp/opencv*

WORKDIR /app

COPY target/cheating-detection-0.0.1-SNAPSHOT.jar app.jar

RUN mkdir -p /app/models /app/evidence

# Set environment variables
ENV MEDIAPIPE_MODEL_DIRECTORY=/app/models
ENV EVIDENCE_FOLDER=/app/evidence
ENV OPENCV_LOADING_METHOD=auto
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -Xmx512m -Djava.library.path=/usr/lib64"
ENV LD_LIBRARY_PATH="/usr/lib64"

# Tạo file kiểm tra OpenCV để chẩn đoán
RUN echo '#!/bin/sh\necho "Testing OpenCV libraries..."\nfind /usr/lib64 -name "*opencv*"\necho "LD_LIBRARY_PATH: $LD_LIBRARY_PATH"\necho "Java Library Path will be: $(echo $JAVA_TOOL_OPTIONS | grep -o "java.library.path=[^ \"]*" || echo "Not set")"\n' > /app/test-opencv.sh && \
    chmod +x /app/test-opencv.sh

# Mở cổng cho ứng dụng
EXPOSE 8081

# Chạy script kiểm tra trước khi chạy ứng dụng
ENTRYPOINT ["/bin/sh", "-c", "/app/test-opencv.sh && java -jar app.jar"]