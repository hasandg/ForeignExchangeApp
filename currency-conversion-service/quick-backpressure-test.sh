#!/bin/bash

BASE_URL="http://localhost:8082/api/v1/backpressure/analysis"

if [ $# -eq 0 ]; then
    echo "Usage: $0 <strategy>"
    echo "Available strategies: LATEST, DROP, BUFFER, ERROR, stats, simulation"
    echo ""
    echo "Examples:"
    echo "  $0 LATEST     # Test onBackpressureLatest() - drops intermediate events"
    echo "  $0 DROP       # Test onBackpressureDrop() - explicit dropping with logging"
    echo "  $0 BUFFER     # Test onBackpressureBuffer() - buffer overflow dropping"
    echo "  $0 ERROR      # Test onBackpressureError() - no dropping, errors instead"
    echo "  $0 stats      # Get backpressure statistics"
    echo "  $0 simulation # Run event loss simulation"
    exit 1
fi

STRATEGY=$1

case $STRATEGY in
    "LATEST"|"latest")
        echo "🧪 Testing LATEST strategy - onBackpressureLatest()"
        echo "This keeps only the latest event, drops all intermediate ones"
        echo "Watch for gaps in event numbers..."
        echo ""
        timeout 10s curl -s "$BASE_URL/test-dropping/LATEST"
        ;;
    "DROP"|"drop")
        echo "🧪 Testing DROP strategy - onBackpressureDrop()"
        echo "This explicitly drops events with logging when consumer is slow"
        echo "Check application logs for 'DROPPED Event' messages"
        echo ""
        timeout 10s curl -s "$BASE_URL/test-dropping/DROP"
        ;;
    "BUFFER"|"buffer")
        echo "🧪 Testing BUFFER strategy - onBackpressureBuffer()"
        echo "This buffers events up to limit, then drops on overflow"
        echo "Check logs for 'BUFFER OVERFLOW' messages"
        echo ""
        timeout 10s curl -s "$BASE_URL/test-dropping/BUFFER"
        ;;
    "ERROR"|"error")
        echo "🧪 Testing ERROR strategy - onBackpressureError()"
        echo "This throws error when backpressure occurs instead of dropping"
        echo "Stream will terminate with error when limit exceeded"
        echo ""
        timeout 5s curl -s "$BASE_URL/test-dropping/ERROR"
        ;;
    "stats"|"STATS")
        echo "📊 Backpressure Statistics"
        echo "=========================="
        curl -s "$BASE_URL/stats" | jq '.' 2>/dev/null || curl -s "$BASE_URL/stats"
        ;;
    "simulation"|"SIMULATION")
        echo "🔥 Event Loss Simulation"
        echo "Fast producer (1ms) vs slow consumer (50ms)"
        echo "This shows real drop rates and statistics"
        echo ""
        timeout 10s curl -s "$BASE_URL/event-loss-simulation"
        ;;
    *)
        echo "❌ Unknown strategy: $STRATEGY"
        echo "Available: LATEST, DROP, BUFFER, ERROR, stats, simulation"
        exit 1
        ;;
esac

echo ""
echo ""
echo "💡 To see dropped events, check application logs:"
echo "tail -f logs/application.log | grep -E 'DROPPED|BUFFER OVERFLOW'" 