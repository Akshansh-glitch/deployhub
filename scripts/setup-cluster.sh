#!/usr/bin/env bash
# One-time cluster setup: Minikube + ingress + metrics-server + Terraform (namespaces, RBAC, monitoring).
set -euo pipefail
cd "$(dirname "$0")/.."

CPUS="${CPUS:-4}"
MEMORY="${MEMORY:-8192}"

echo "==> Starting Minikube (${CPUS} CPUs, ${MEMORY} MB)"
minikube status >/dev/null 2>&1 || minikube start --cpus="$CPUS" --memory="$MEMORY" --driver=docker

echo "==> Enabling ingress and metrics-server add-ons"
minikube addons enable ingress
minikube addons enable metrics-server

echo "==> Applying Terraform (namespaces, quotas, RBAC, Prometheus + Grafana)"
cd terraform
[ -f terraform.tfvars ] || { cp terraform.tfvars.example terraform.tfvars; echo "    created terraform/terraform.tfvars from the example"; }
terraform init -input=false
terraform apply -input=false -auto-approve
cd ..

echo
echo "==> Done. Your APP_DOMAIN for backend/.env:"
case "$(uname -s)" in
  Linux)
    echo "    APP_DOMAIN=$(minikube ip).nip.io"
    ;;
  *)
    echo "    APP_DOMAIN=127.0.0.1.nip.io"
    echo "    and keep \`minikube tunnel\` running in its own terminal (it asks for your password)."
    ;;
esac
echo "Next: scripts/port-forward.sh, then scripts/run-backend.sh and scripts/run-frontend.sh"
