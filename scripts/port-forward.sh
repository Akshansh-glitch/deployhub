#!/usr/bin/env bash
# Exposes Prometheus on :9090 and Grafana on :3000 for the locally running backend and your browser.
set -euo pipefail
echo "Prometheus -> http://localhost:9090"
echo "Grafana    -> http://localhost:3000  (user admin)"
echo "Ctrl+C to stop."
kubectl -n monitoring port-forward svc/kps-prometheus 9090:9090 &
PF1=$!
kubectl -n monitoring port-forward svc/kps-grafana 3000:80 &
PF2=$!
trap 'kill $PF1 $PF2 2>/dev/null' EXIT
wait
