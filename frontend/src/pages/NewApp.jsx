import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, RUNTIMES } from "../api.js";

export default function NewApp() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    name: "",
    repoUrl: "",
    branch: "main",
    appPath: "",
    runtime: "SPRINGBOOT",
    port: 8080,
    healthPath: "",
    replicas: 1,
  });
  const [deployNow, setDeployNow] = useState(true);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });
  const pickRuntime = (runtime) =>
    setForm({ ...form, runtime, port: RUNTIMES[runtime].port, healthPath: "" });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const app = await api("/apps", {
        method: "POST",
        body: {
          ...form,
          port: Number(form.port),
          replicas: Number(form.replicas),
          healthPath: form.healthPath || null,
        },
      });
      if (deployNow) {
        await api(`/apps/${app.id}/deploy`, { method: "POST" }).catch(() => {});
      }
      navigate(`/apps/${app.id}`);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <>
      <p className="crumbs">
        <Link to="/">Apps</Link> / New app
      </p>
      <h1>New app</h1>

      <form className="form form-wide" onSubmit={submit}>
        <fieldset>
          <legend>Where the code lives</legend>
          <label>
            Repository URL
            <input required placeholder="https://github.com/you/your-repo" value={form.repoUrl} onChange={set("repoUrl")} />
            <span className="hint">Public GitHub repositories only.</span>
          </label>
          <div className="row">
            <label>
              Branch
              <input required value={form.branch} onChange={set("branch")} />
            </label>
            <label>
              Folder (optional)
              <input placeholder="sample-apps/hello-spring" value={form.appPath} onChange={set("appPath")} />
              <span className="hint">Leave empty if the app is at the repository root.</span>
            </label>
          </div>
        </fieldset>

        <fieldset>
          <legend>How it runs</legend>
          <div className="segmented" role="radiogroup" aria-label="Runtime">
            {Object.entries(RUNTIMES).map(([key, r]) => (
              <button
                type="button"
                key={key}
                role="radio"
                aria-checked={form.runtime === key}
                className={form.runtime === key ? "selected" : ""}
                onClick={() => pickRuntime(key)}
              >
                {r.label}
              </button>
            ))}
          </div>
          <div className="row">
            <label>
              Name
              <input
                required
                pattern="[a-z]([-a-z0-9]{0,28}[a-z0-9])?"
                placeholder="hello-spring"
                value={form.name}
                onChange={set("name")}
              />
              <span className="hint">Lowercase letters, numbers and dashes. Becomes part of the app's address.</span>
            </label>
            <label>
              Port
              <input type="number" min="1" max="65535" required value={form.port} onChange={set("port")} />
            </label>
          </div>
          <div className="row">
            <label>
              Health check path (optional)
              <input
                placeholder={form.runtime === "SPRINGBOOT" ? "/actuator/health" : "/healthz"}
                value={form.healthPath}
                onChange={set("healthPath")}
              />
              <span className="hint">Without one, Kubernetes only checks that the port is open.</span>
            </label>
            <label>
              Replicas
              <input type="number" min="1" max="5" required value={form.replicas} onChange={set("replicas")} />
            </label>
          </div>
        </fieldset>

        <label className="checkbox">
          <input type="checkbox" checked={deployNow} onChange={(e) => setDeployNow(e.target.checked)} />
          Deploy as soon as the app is created
        </label>

        {error && <p className="error" role="alert">{error}</p>}
        <div className="actions">
          <button className="btn btn-primary" disabled={busy}>
            {deployNow ? "Create and deploy" : "Create app"}
          </button>
          <Link to="/" className="btn btn-quiet">
            Cancel
          </Link>
        </div>
      </form>
    </>
  );
}
