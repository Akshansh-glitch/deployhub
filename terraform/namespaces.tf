# Three namespaces: the platform itself, user apps, and monitoring.

resource "kubernetes_namespace_v1" "system" {
  metadata {
    name   = "deployhub-system"
    labels = { "app.kubernetes.io/part-of" = "deployhub" }
  }
}

resource "kubernetes_namespace_v1" "apps" {
  metadata {
    name   = var.apps_namespace
    labels = { "app.kubernetes.io/part-of" = "deployhub" }
  }
}

resource "kubernetes_namespace_v1" "monitoring" {
  metadata {
    name   = "monitoring"
    labels = { "app.kubernetes.io/part-of" = "deployhub" }
  }
}

# Guard rails so one bad app cannot starve the laptop-sized cluster.
resource "kubernetes_resource_quota_v1" "apps" {
  metadata {
    name      = "apps-quota"
    namespace = kubernetes_namespace_v1.apps.metadata[0].name
  }
  spec {
    hard = {
      "pods"            = "20"
      "requests.cpu"    = "3"
      "requests.memory" = "4Gi"
      "limits.cpu"      = "8"
      "limits.memory"   = "8Gi"
    }
  }
}

resource "kubernetes_limit_range_v1" "apps" {
  metadata {
    name      = "apps-defaults"
    namespace = kubernetes_namespace_v1.apps.metadata[0].name
  }
  spec {
    limit {
      type = "Container"
      default = {
        cpu    = "500m"
        memory = "512Mi"
      }
      default_request = {
        cpu    = "100m"
        memory = "128Mi"
      }
    }
  }
}

# Lets Kubernetes pull private images from GHCR. Only created when a token is supplied.
resource "kubernetes_secret_v1" "ghcr_pull" {
  count = var.ghcr_token == "" ? 0 : 1

  metadata {
    name      = "ghcr-pull"
    namespace = kubernetes_namespace_v1.apps.metadata[0].name
  }
  type = "kubernetes.io/dockerconfigjson"
  data = {
    ".dockerconfigjson" = jsonencode({
      auths = {
        "ghcr.io" = {
          auth = base64encode("${var.ghcr_username}:${var.ghcr_token}")
        }
      }
    })
  }
}
