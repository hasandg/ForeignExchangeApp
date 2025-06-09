#!/bin/bash

echo "🚀 Starting Backpressure Analysis Tests"
echo "========================================"

BASE_URL="http://localhost:8082/api/v1/backpressure/analysis"

echo ""
echo "📊 Getting backpressure statistics..."
curl -s "$BASE_URL/stats" | jq '.' || curl -s "$BASE_URL/stats"

echo ""
echo ""
echo "🧪 Testing LATEST strategy (drops intermediate events)..."
echo "This will show only the latest events being delivered"
timeout 10s curl -s "$BASE_URL/test-dropping/LATEST" | head -20

echo ""
echo ""
echo "🧪 Testing DROP strategy (explicit event dropping)..."
echo "This will show events being explicitly dropped with logging"
timeout 10s curl -s "$BASE_URL/test-dropping/DROP" | head -20

echo ""
echo ""
echo "🧪 Testing BUFFER strategy (buffer overflow dropping)..."
echo "This will show events being dropped when buffer is full"
timeout 10s curl -s "$BASE_URL/test-dropping/BUFFER" | head -20

echo ""
echo ""
echo "🧪 Testing ERROR strategy (no dropping, errors instead)..."
echo "This will show backpressure errors when limit is exceeded"
timeout 5s curl -s "$BASE_URL/test-dropping/ERROR" | head -10

echo ""
echo ""
echo "🔥 Event Loss Simulation (shows real drop rates)..."
echo "This simulates fast producer with slow consumer"
timeout 15s curl -s "$BASE_URL/event-loss-simulation" | head -30

echo ""
echo ""
echo "⚠️  No Backpressure Example (dangerous without backpressure)..."
echo "This shows what happens without proper backpressure handling"
timeout 5s curl -s "$BASE_URL/comparison/no-backpressure" | head -10

echo ""
echo ""
echo "📋 Summary of Current Implementation:"
echo "======================================"
curl -s "$BASE_URL/stats" | jq '.currentImplementation' || echo "Install jq for better formatting"

echo ""
echo ""
echo "✅ Backpressure Analysis Tests Completed!"
echo "Check the logs to see dropped events and backpressure behavior"
echo ""
echo "To run individual tests:"
echo "curl '$BASE_URL/test-dropping/LATEST'"
echo "curl '$BASE_URL/test-dropping/DROP'"
echo "curl '$BASE_URL/test-dropping/BUFFER'"
echo "curl '$BASE_URL/test-dropping/ERROR'"
echo "curl '$BASE_URL/event-loss-simulation'"
echo "curl '$BASE_URL/stats'" 