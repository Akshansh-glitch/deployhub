#!/usr/bin/env bash
# Optional: run DeployHub itself inside Minikube instead of on your laptop.
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f k8s/platform/secrets.yaml ] || { echo "Create k8s/platform/secrets.yaml from secrets.example.yaml first." >&2; exit 1; }

echo "==> Building images inside Minikube"
minikube image build -t deployhub-backend:local backend
minikube image build -t deployhub-frontend:local frontend

echo "==> Applying manifests"
kubectl apply -f k8s/platform/secrets.yaml
kubectl apply -f k8s/platform/postgres.yaml -f k8s/platform/backend.yaml -f k8s/platform/frontend.yaml
kubectl -n deployhub-system rollout status deploy/deployhub-backend --timeout=300s
kubectl -n deployhub-system rollout status deploy/deployhub-frontend --timeout=120s
echo "==> Open the host from k8s/platform/frontend.yaml (default http://deployhub.127.0.0.1.nip.io)"
