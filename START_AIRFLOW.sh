#!/bin/bash

echo "=================================="
echo "  C2JAVA Airflow 시작 스크립트"
echo "=================================="
echo ""

cd /Users/dongsoo/Desktop/C2JAVA/docker/compose

echo "📦 1. Docker 컨테이너 확인 중..."
docker ps --filter "name=c2java" --format "table {{.Names}}\t{{.Status}}"
echo ""

echo "🚀 2. Airflow 서비스 시작 중..."
docker-compose up -d postgres airflow-init
sleep 5

docker-compose up -d airflow-webserver airflow-scheduler
echo ""

echo "⏳ 3. Airflow 초기화 대기 중 (30초)..."
sleep 30

echo ""
echo "✅ Airflow 시작 완료!"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 Airflow Web UI"
echo "   URL: http://localhost:8081"
echo "   ID: admin"
echo "   PW: admin"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "💡 이제 C2JAVA 웹 페이지에서"
echo "   C 파일을 업로드하고 변환을 요청하면"
echo "   Airflow가 자동으로 변환을 실행합니다!"
echo ""
echo "🔍 모니터링 방법:"
echo "   1. C2JAVA: http://localhost:3000/jobs"
echo "   2. Airflow: http://localhost:8081"
echo ""
