@echo off
echo Starting Log Sleuth Enterprise Infrastructure...

:: Проверяем установлен ли Docker
docker --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker is not installed. Please install Docker first.
    exit /b 1
)

:: Проверяем установлен ли Docker Compose
docker-compose --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker Compose is not installed. Please install Docker Compose first.
    exit /b 1
)

echo 📦 Starting containers...
docker-compose up -d

echo ⏳ Waiting for services to start...
timeout /t 30 /nobreak >nul

echo ✅ Infrastructure started successfully!
echo.
echo 📊 Services:
echo    - Cassandra:      localhost:9042
echo    - Elasticsearch:  localhost:9200
echo    - Kafka:          localhost:9092
echo    - Zookeeper:      localhost:2181
echo.
echo 🛑 To stop infrastructure: docker-compose down
echo 📋 To view logs: docker-compose logs -f
pause