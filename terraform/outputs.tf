output "apps_namespace" {
  value = kubernetes_namespace_v1.apps.metadata[0].name
}

output "pull_secret_created" {
  value = length(kubernetes_secret_v1.ghcr_pull) > 0
}

output "next_steps" {
  value = <<-EOT
    Prometheus: kubectl -n monitoring port-forward svc/kps-prometheus 9090:9090
    Grafana:    kubectl -n monitoring port-forward svc/kps-grafana 3000:80   (admin / your grafana_admin_password)
    (or run scripts/port-forward.sh)
  EOT
}
