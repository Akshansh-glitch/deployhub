/**
 * The deployment drawn as a line with five stations, like a transit map.
 * Each station is: done, active, failed, skipped or waiting.
 */
const STATIONS = ["Source", "Build and test", "Image", "Rollout", "Live"];

function stationStates(d) {
  if (!d) return STATIONS.map(() => "waiting");
  const rollback = d.trigger === "ROLLBACK";
  const s = (arr) => (rollback ? arr.map((x, i) => (i === 1 && x === "done" ? "skipped" : x)) : arr);
  switch (d.status) {
    case "QUEUED":
      return ["active", "waiting", "waiting", "waiting", "waiting"];
    case "BUILDING":
      return ["done", "active", "waiting", "waiting", "waiting"];
    case "BUILD_FAILED":
      return ["done", "failed", "waiting", "waiting", "waiting"];
    case "IMAGE_PUSHED":
      return s(["done", "done", "done", "active", "waiting"]);
    case "DEPLOYING":
      return s(["done", "done", "done", "active", "waiting"]);
    case "DEPLOY_FAILED":
      return s(["done", "done", "done", "failed", "waiting"]);
    default: // RUNNING, SUPERSEDED
      return s(["done", "done", "done", "done", "done"]);
  }
}

export default function Pipeline({ app, deployment }) {
  const states = stationStates(deployment);
  const details = [
    <>
      {app.repoUrl.replace("https://github.com/", "")}
      <br />
      {app.branch}
      {app.appPath ? ` / ${app.appPath}` : ""}
    </>,
    deployment?.trigger === "ROLLBACK" ? (
      "Skipped for rollback"
    ) : deployment?.workflowRunUrl ? (
      <a href={deployment.workflowRunUrl} target="_blank" rel="noreferrer">
        GitHub Actions run
      </a>
    ) : deployment ? (
      "Waiting for GitHub Actions"
    ) : (
      "Runs your tests"
    ),
    deployment?.imageTag ? <code>{deployment.imageTag}</code> : "Pushed to GHCR",
    deployment && ["DEPLOYING", "DEPLOY_FAILED", "IMAGE_PUSHED"].includes(deployment.status)
      ? deployment.message
      : "Kubernetes",
    states[4] === "done" ? (
      <a href={app.url} target="_blank" rel="noreferrer">
        {app.url.replace("http://", "")}
      </a>
    ) : (
      app.url.replace("http://", "")
    ),
  ];

  return (
    <ol className="pipeline" aria-label="Deployment progress">
      {STATIONS.map((name, i) => (
        <li key={name} className={`station station-${states[i]}`}>
          <span className="station-dot" aria-hidden="true" />
          <span className="station-name">
            {name}
            <span className="sr-only"> ({states[i]})</span>
          </span>
          <span className="station-detail">{details[i]}</span>
        </li>
      ))}
    </ol>
  );
}

/** A compact five-dot version for the app list. */
export function MiniPipeline({ deployment }) {
  const states = stationStates(deployment);
  return (
    <span className="mini-pipeline" aria-hidden="true">
      {states.map((s, i) => (
        <span key={i} className={`mini-dot station-${s}`} />
      ))}
    </span>
  );
}
