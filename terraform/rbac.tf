# Least privilege for the backend when it runs inside the cluster:
# it may only manage Deployments, Services, Ingresses and Pods in the apps namespace.

resource "kubernetes_service_account_v1" "backend" {
  metadata {
    name      = "deployhub-backend"
    namespace = kubernetes_namespace_v1.system.metadata[0].name
  }
}

resource "kubernetes_role_v1" "app_manager" {
  metadata {
    name      = "deployhub-app-manager"
    namespace = kubernetes_namespace_v1.apps.metadata[0].name
  }

  rule {
    api_groups = ["apps"]
    resources  = ["deployments", "deployments/scale"]
    verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
  }

  rule {
    api_groups = [""]
    resources  = ["services"]
    verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
  }

  rule {
    api_groups = [""]
    resources  = ["pods"]
    verbs      = ["get", "list", "watch", "delete"]
  }

  rule {
    api_groups = [""]
    resources  = ["pods/log"]
    verbs      = ["get"]
  }

  rule {
    api_groups = ["networking.k8s.io"]
    resources  = ["ingresses"]
    verbs      = ["get", "list", "watch", "create", "update", "patch", "delete"]
  }
}

resource "kubernetes_role_binding_v1" "app_manager" {
  metadata {
    name      = "deployhub-app-manager"
    namespace = kubernetes_namespace_v1.apps.metadata[0].name
  }
  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "Role"
    name      = kubernetes_role_v1.app_manager.metadata[0].name
  }
  subject {
    kind      = "ServiceAccount"
    name      = kubernetes_service_account_v1.backend.metadata[0].name
    namespace = kubernetes_namespace_v1.system.metadata[0].name
  }
}
