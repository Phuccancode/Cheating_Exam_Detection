Write-Host "Running Maven build..."
mvn clean package


if ($LASTEXITCODE -ne 0) {
    Write-Host "An error occurred."
    exit 1
}


docker build -t phuccancode/cheating-detection .

if ($LASTEXITCODE -ne 0) {
    Write-Host "Lỗi khi build Docker."
    exit 1
}

Write-Host "Xóa các Docker images cũ..."
docker image prune -f


docker stop cheating-detection-app

docker rm cheating-detection-app

docker run --name cheating-detection-app --network phuccancode-network -p 9091:8081 -e DBMS_CONNECTION=jdbc:postgresql://my-postgres:5432/cheating_detection -d phuccancode/cheating-detection

if ($LASTEXITCODE -ne 0) {
    Write-Host "Lỗi khi khởi động Docker."
    exit 1
}

# Delay 3s trước khi thông báo thành công
Start-Sleep -Seconds 5

Write-Host "Deploy thành công!"
Write-Host "Thành công!`nTruy cập http://localhost:9091/exam-monitor"

exit 0