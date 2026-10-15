terraform {
  required_version = ">= 1.5"

  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.31"
    }
    # Pinned to 2.x: helm provider 3.x changed the provider block syntax.
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.17"
    }
  }
}
