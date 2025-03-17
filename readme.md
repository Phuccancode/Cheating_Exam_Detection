docker run --network phuccancode-network --name my-postgres -e POSTGRES_USER=root -e POSTGRES_PASSWORD=root -p 5432:5432 -d postgres:16
docker build -t phuccancode/cheating-detection .
docker run --name cheating-detection-app --network phuccancode-network -p 9091:8081 -v $(pwd)/evidence_docker:/app/evidence_docker -e DBMS_CONNECTION=jdbc:postgresql://my-postgres:5432/cheating_detection -d phuccancode/cheating-detection

docker image push phuccancode/cheating-detection