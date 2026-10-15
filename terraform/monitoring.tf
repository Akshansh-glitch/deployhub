# Prometheus + Grafana + kube-state-metrics + node-exporter in one Helm release.
resource "helm_release" "monitoring" {
  name       = "kps"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  version    = var.monitoring_chart_version
  namespace  = kubernetes_namespace_v1.monitoring.metadata[0].name
  timeout    = 900

  values = [yamlencode({
    # Short, predictable names: services become kps-prometheus and kps-grafana.
    fullnameOverride = "kps"

    alertmanager = { enabled = false }

    grafana = {
      adminPassword = var.grafana_admin_password
      sidecar = {
        dashboards = { enabled = true, label = "grafana_dashboard" }
      }
    }

    prometheus = {
      prometheusSpec = {
        retention = "2d"
        # Pick up every ServiceMonitor/PodMonitor, not only ones carrying this release's label.
        serviceMonitorSelectorNilUsesHelmValues = false
        podMonitorSelectorNilUsesHelmValues     = false
        resources = {
          requests = { cpu = "200m", memory = "512Mi" }
          limits   = { memory = "1Gi" }
        }
      }
      # Scrape request metrics from every Spring Boot app DeployHub deploys.
      # Defined through chart values so Terraform never has to plan a CRD that does not exist yet.
      additionalServiceMonitors = [{
        name              = "deployhub-springboot-apps"
        namespaceSelector = { matchNames = [var.apps_namespace] }
        selector          = { matchLabels = { "deployhub.io/runtime" = "springboot" } }
        endpoints = [{
          port     = "http"
          path     = "/actuator/prometheus"
          interval = "15s"
        }]
      }]
    }
  })]
}

# The Grafana sidecar loads any ConfigMap labelled grafana_dashboard=1 as a dashboard.
resource "kubernetes_config_map_v1" "deployhub_dashboard" {
  metadata {
    name      = "deployhub-dashboard"
    namespace = kubernetes_namespace_v1.monitoring.metadata[0].name
    labels    = { grafana_dashboard = "1" }
  }
  data = {
    "deployhub.json" = file("${path.module}/../monitoring/dashboards/deployhub.json")
  }
}
