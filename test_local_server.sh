#!/bin/bash

echo "=== Testing Local Server Management ==="
echo ""

# Airflow 상태 확인
echo "1. Testing Airflow status check..."
docker ps --filter "name=c2java-airflow" --format "{{.Names}}\t{{.Status}}"
echo ""

# CLI Service 상태 확인
echo "2. Testing CLI Service status check..."
pgrep -f "cli_service.py"
echo ""

# Airflow 중지 테스트
echo "3. Testing Airflow stop..."
cd /Users/dongsoo/Desktop/C2JAVA/airflow
docker-compose -f docker-compose.yml ps
echo ""

# Airflow 시작 명령어 확인
echo "4. Airflow start command would be:"
echo "  cd /Users/dongsoo/Desktop/C2JAVA/airflow && docker-compose up -d"
echo ""

# CLI Service 시작 명령어 확인
echo "5. CLI Service start command would be:"
echo "  cd /Users/dongsoo/Desktop/C2JAVA/cli && python3 cli_service.py"
echo ""

echo "=== Test Complete ==="
