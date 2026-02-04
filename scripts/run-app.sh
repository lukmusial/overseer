#!/usr/bin/env bash
#
# Start the HFT application.
# Kills any prior instance, optionally rebuilds the frontend, and launches bootRun.
#
# Usage:
#   ./scripts/run-app.sh              # Backend only (skip frontend build)
#   ./scripts/run-app.sh --full       # Rebuild frontend + start app
#   ./scripts/run-app.sh --frontend   # Rebuild frontend only (no app start)
#

set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${1:-}"

kill_existing() {
    if pgrep -f HftApplication > /dev/null 2>&1; then
        echo "Stopping existing HFT instance..."
        pkill -f HftApplication 2>/dev/null || true
        sleep 3
        echo "Stopped."
    fi
}

build_frontend() {
    echo "Building frontend..."
    (cd hft-ui && npm run build)
    rm -rf hft-app/src/main/resources/static/assets
    cp -r hft-ui/dist/* hft-app/src/main/resources/static/
    echo "Frontend built and copied to static resources."
}

start_app() {
    echo "Starting application..."
    ./gradlew :hft-app:bootRun -PskipFrontend
}

case "$MODE" in
    --frontend)
        build_frontend
        ;;
    --full)
        kill_existing
        build_frontend
        start_app
        ;;
    *)
        kill_existing
        start_app
        ;;
esac
