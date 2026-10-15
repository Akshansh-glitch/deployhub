# DeployHub: context for Claude Code

Educational PaaS for a 5-person Cloud Computing & DevOps course project. Read README.md for the architecture,
deployment state machine and setup. Keep the scope small: this is a student project, not production.

## Current status

All code was written in a sandbox that could not download Maven dependencies or Terraform providers and had no cluster.
Verified there: frontend build + UI screenshots against a mock API, Node/Flask sample tests, actionlint on both workflows,
kubeconform on k8s/platform, Java syntax.

First tasks 1–5 below are done (2026-10-15): both Maven builds and Terraform pass, and the README demo script ran
end to end on Minikube. The only code fix needed was a `preStop` sleep in `ManifestFactory` for zero-downtime
rollouts. See README "Verification status" for details.

## First tasks, in order

1. `cd backend && mvn -B verify`. Fix compile errors. Likeliest spots:
   - `k8s/KubernetesService.apply()`: fabric8 6.13 `client.resource(x).forceConflicts().serverSideApply()` chain.
   - `k8s/ManifestFactory`: fabric8 builder method names (Ingress backend/service/port nesting, probes).
   Keep the behaviour (server-side apply, zero-downtime strategy, startup probe); only fix the API usage.
2. `cd sample-apps/hello-spring && mvn -B test`.
3. `terraform -chdir=terraform fmt -recursive && terraform -chdir=terraform init -backend=false && terraform -chdir=terraform validate`.
4. Bring the stack up per README "Setup" and get one end-to-end deploy of `sample-apps/hello-spring` working.
   Check: run lookup by `display_title` (GitHubActionsClient.findRun), GHCR pull, Ingress URL, Prometheus metrics.
5. Then run the README demo script start to finish and fix whatever breaks.

## Commands

- Cluster: `scripts/setup-cluster.sh` (Minikube + add-ons + `terraform apply`)
- Monitoring ports: `scripts/port-forward.sh`
- Backend: `scripts/run-backend.sh` (needs `backend/.env`, see `.env.example`)
- Frontend: `scripts/run-frontend.sh` → http://localhost:5173 (proxies /api to :8080)
- Backend tests: `cd backend && mvn -B verify` (unit tests only; no DB or cluster needed)
- Frontend build: `cd frontend && npm ci && npm run build`

## Invariants (don't break these)

- The build workflow's `run-name: deploy-<deployment_id>` must match `GitHubActionsClient.runName()`.
- Workflow inputs reach shell steps only through `env:`, never `${{ inputs.* }}` inside `run:` (script injection).
- Image names come from `ImageNames.forDeployment()`; GHCR owner must be lowercase.
- Every k8s object for an app is named after the app and carries labels `app=<name>` and `deployhub.io/runtime=<runtime>`.
  The Service port must be named `http` (Prometheus ServiceMonitor in terraform/monitoring.tf relies on it).
- The Deployment strategy stays `maxSurge: 1, maxUnavailable: 0`, and containers keep the `preStop` sleep
  (the zero-downtime demo depends on both).
- The backend only touches the `apps` namespace (Terraform RBAC in terraform/rbac.tf enforces this in-cluster).
- API errors are always `{"error": "message"}` (GlobalExceptionHandler); the frontend shows them verbatim.
- DB schema changes go in a new Flyway migration (`V2__...sql`), never by editing V1.

## Style

- Backend: Java 17, Spring Boot 3.3, constructor injection, records for DTOs (all in `dto/Dtos.java`).
- Frontend: plain React + fetch (no axios, no state library). Design tokens are CSS variables in `styles.css`,
  with light and dark values. UI copy is sentence case and plain language.
