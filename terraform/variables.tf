variable "kubeconfig_path" {
  description = "Path to the kubeconfig that points at Minikube."
  type        = string
  default     = "~/.kube/config"
}

variable "kube_context" {
  description = "kubectl context to use."
  type        = string
  default     = "minikube"
}

variable "apps_namespace" {
  description = "Namespace where user applications run."
  type        = string
  default     = "apps"
}

variable "ghcr_username" {
  description = "GitHub username used to pull private images from GHCR. Leave empty if the package is public."
  type        = string
  default     = ""
}

variable "ghcr_token" {
  description = "Classic GitHub PAT with read:packages, for pulling images. Leave empty if the package is public."
  type        = string
  default     = ""
  sensitive   = true
}

variable "grafana_admin_password" {
  description = "Grafana admin password."
  type        = string
  default     = "deployhub"
  sensitive   = true
}

variable "monitoring_chart_version" {
  description = "kube-prometheus-stack chart version. null = latest; pin it after your first successful install."
  type        = string
  default     = null
}
