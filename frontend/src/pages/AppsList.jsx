import { Link } from "react-router-dom";
import { api, RUNTIMES, timeAgo, usePoll } from "../api.js";
import StatusBadge from "../components/StatusBadge.jsx";
import { MiniPipeline } from "../components/Pipeline.jsx";

export default function AppsList() {
  const { data: apps, error } = usePoll(() => api("/apps"), 5000);

  return (
    <>
      <div className="page-head">
        <h1>Your apps</h1>
        <Link to="/apps/new" className="btn btn-primary">
          New app
        </Link>
      </div>

      {error && <p className="error" role="alert">{error}</p>}
      {!apps && !error && <p className="muted">Loading…</p>}

      {apps && apps.length === 0 && (
        <div className="empty">
          <h2>Deploy your first app</h2>
          <p>
            Point DeployHub at a GitHub repository. It builds and tests the code, packages it as a container and runs it
            on Kubernetes. Try the sample app in this project: folder <code>sample-apps/hello-spring</code>.
          </p>
          <Link to="/apps/new" className="btn btn-primary">
            New app
          </Link>
        </div>
      )}

      {apps && apps.length > 0 && (
        <ul className="app-list">
          {apps.map((app) => (
            <li key={app.id}>
              <Link to={`/apps/${app.id}`} className="app-row">
                <span className="app-row-name">
                  <strong>{app.name}</strong>
                  <span className="muted small">{RUNTIMES[app.runtime]?.label}</span>
                </span>
                <MiniPipeline deployment={app.latestDeployment} />
                <StatusBadge status={app.latestDeployment?.status} />
                <span className="muted small app-row-when">
                  {app.latestDeployment ? timeAgo(app.latestDeployment.createdAt) : "—"}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
