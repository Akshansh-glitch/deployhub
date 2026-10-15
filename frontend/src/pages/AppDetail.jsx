import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { age, api, duration, RUNTIMES, timeAgo, usePoll } from "../api.js";
import Pipeline from "../components/Pipeline.jsx";
import StatusBadge from "../components/StatusBadge.jsx";
import MetricsCharts from "../components/MetricsCharts.jsx";
import LogsDialog from "../components/LogsDialog.jsx";

const ACTIVE = ["QUEUED", "BUILDING", "IMAGE_PUSHED", "DEPLOYING"];
// Waiting reasons that mean a pod is broken, not just starting up
const POD_FAILURES = ["ImagePullBackOff", "ErrImagePull", "InvalidImageName", "CrashLoopBackOff", "CreateContainerConfigError", "CreateContainerError", "Error", "OOMKilled"];

export default function AppDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const app = usePoll(() => api(`/apps/${id}`), 3000, [id]);
  const status = usePoll(() => api(`/apps/${id}/status`), 3000, [id]);
  const history = usePoll(() => api(`/apps/${id}/deployments`), 4000, [id]);
  const metrics = usePoll(() => api(`/apps/${id}/metrics?minutes=15`), 15000, [id]);

  const [config, setConfig] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [logsPod, setLogsPod] = useState(null);
  const [replicas, setReplicas] = useState(null);

  useEffect(() => {
    api("/config").then(setConfig).catch(() => {});
  }, []);

  useEffect(() => {
    if (status.data && replicas === null) setReplicas(status.data.desired);
  }, [status.data, replicas]);

  const run = async (fn) => {
    setActionError(null);
    try {
      await fn();
      app.refresh();
      status.refresh();
      history.refresh();
    } catch (e) {
      setActionError(e.message);
    }
  };

  if (app.error && !app.data) return <p className="error" role="alert">{app.error}</p>;
  if (!app.data) return <p className="muted">Loading…</p>;

  const a = app.data;
  const latest = a.latestDeployment;
  const busy = latest && ACTIVE.includes(latest.status);
  const s = status.data;

  const deploy = () => run(() => api(`/apps/${id}/deploy`, { method: "POST" }));
  const scale = (n) => {
    setReplicas(n);
    run(() => api(`/apps/${id}/scale`, { method: "PATCH", body: { replicas: n } }));
  };
  const killPod = (pod) => run(() => api(`/apps/${id}/pods/${pod}`, { method: "DELETE" }));
  const rollback = (depId) =>
    run(() => api(`/apps/${id}/deployments/${depId}/rollback`, { method: "POST" }));
  const remove = async () => {
    if (!window.confirm(`Delete ${a.name}? This removes it from Kubernetes and deletes its history.`)) return;
    try {
      await api(`/apps/${id}`, { method: "DELETE" });
      navigate("/");
    } catch (e) {
      setActionError(e.message);
    }
  };

  return (
    <>
      <p className="crumbs">
        <Link to="/">Apps</Link> / {a.name}
      </p>
      <div className="page-head">
        <div>
          <h1>{a.name}</h1>
          <p className="muted">
            {RUNTIMES[a.runtime]?.label} on port {a.port}
            {s?.state === "HEALTHY" && (
              <>
                {", live at "}
                <a href={a.url} target="_blank" rel="noreferrer">
                  {a.url.replace("http://", "")}
                </a>
              </>
            )}
          </p>
        </div>
        <div className="actions">
          <button className="btn btn-primary" onClick={deploy} disabled={busy}>
            {busy ? "Deploying…" : latest ? "Deploy latest commit" : "Deploy"}
          </button>
          <button className="btn btn-danger-quiet" onClick={remove}>
            Delete app
          </button>
        </div>
      </div>

      {actionError && <p className="error" role="alert">{actionError}</p>}
      {config && !config.githubConfigured && (
        <p className="notice">
          The server has no GitHub token, so builds can't start. Set <code>GITHUB_TOKEN</code> and{" "}
          <code>GITHUB_OWNER</code> in <code>backend/.env</code> and restart the backend.
        </p>
      )}

      <section className="section">
        <div className="section-head">
          <h2>{latest ? `Deployment #${latest.id}` : "No deployments yet"}</h2>
          {latest && <StatusBadge status={latest.status} />}
        </div>
        <Pipeline app={a} deployment={latest} />
        {latest?.message && ["BUILD_FAILED", "DEPLOY_FAILED"].includes(latest.status) && (
          <p className="error">{latest.message}</p>
        )}
      </section>

      <div className="split">
        <section className="section">
          <div className="section-head">
            <h2>Pods</h2>
            {s && <StatusBadge status={s.state} />}
          </div>
          {s && s.state !== "NOT_DEPLOYED" && (
            <div className="replicas">
              <span>
                <strong>{s.available}</strong> of {s.desired} available
              </span>
              <div className="stepper" aria-label="Replicas">
                <button className="btn btn-quiet" onClick={() => scale(Math.max(0, (replicas ?? s.desired) - 1))} aria-label="Remove a replica">
                  −
                </button>
                <span aria-live="polite">{replicas ?? s.desired}</span>
                <button className="btn btn-quiet" onClick={() => scale(Math.min(5, (replicas ?? s.desired) + 1))} aria-label="Add a replica">
                  +
                </button>
              </div>
            </div>
          )}
          {s?.pods?.length ? (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Pod</th>
                    <th>State</th>
                    <th>
                      <span className="sr-only">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {s.pods.map((p) => (
                    <tr key={p.name} className={p.terminating ? "row-fading" : ""}>
                      <td>
                        <code className="pod-name">{p.name}</code>
                        <div className="muted small">{p.imageTag}</div>
                      </td>
                      <td>
                        <span className={`dot ${p.terminating ? "dot-idle" : p.ready ? "dot-good" : POD_FAILURES.includes(p.reason) ? "dot-bad" : "dot-progress"}`} />
                        {p.terminating ? "Terminating" : p.ready ? "Ready" : p.reason || p.phase}
                        <div className="muted small">
                          {age(p.startedAt)} old
                          {p.restarts > 0 && `, ${p.restarts} restart${p.restarts > 1 ? "s" : ""}`}
                        </div>
                      </td>
                      <td className="cell-actions cell-actions-stack">
                        <button className="btn-link" onClick={() => setLogsPod(p.name)}>
                          Logs
                        </button>
                        <button
                          className="btn-link link-danger"
                          disabled={p.terminating}
                          title="Kubernetes will start a replacement automatically"
                          onClick={() => killPod(p.name)}
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="muted">{s?.state === "STOPPED" ? "Scaled to zero." : "No pods running yet."}</p>
          )}
        </section>

        <section className="section">
          <div className="section-head">
            <h2>Metrics</h2>
          </div>
          <MetricsCharts
            metrics={metrics.data}
            grafanaUrl={config?.grafanaUrl}
            showRequests={a.runtime === "SPRINGBOOT"}
          />
        </section>
      </div>

      <section className="section">
        <div className="section-head">
          <h2>History</h2>
        </div>
        {history.data?.length ? (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Status</th>
                  <th>Version</th>
                  <th>Started</th>
                  <th>Took</th>
                  <th>
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {history.data.map((d) => (
                  <tr key={d.id}>
                    <td>{d.id}</td>
                    <td>
                      <StatusBadge status={d.status} />
                      {d.trigger === "ROLLBACK" && <span className="muted small"> rollback</span>}
                    </td>
                    <td>
                      <code>{d.imageTag ?? "—"}</code>
                    </td>
                    <td>{timeAgo(d.createdAt)}</td>
                    <td>{duration(d.createdAt, d.finishedAt)}</td>
                    <td className="cell-actions">
                      {d.workflowRunUrl && (
                        <a href={d.workflowRunUrl} target="_blank" rel="noreferrer">
                          Build log
                        </a>
                      )}
                      {d.status === "SUPERSEDED" && d.imageTag && (
                        <button className="btn-link" disabled={busy} onClick={() => rollback(d.id)}>
                          Roll back to this
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="muted">Deployments will appear here.</p>
        )}
      </section>

      {logsPod && <LogsDialog appId={id} pod={logsPod} onClose={() => setLogsPod(null)} />}
    </>
  );
}
