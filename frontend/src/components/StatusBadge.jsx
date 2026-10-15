const LABELS = {
  QUEUED: ["Queued", "progress"],
  BUILDING: ["Building", "progress"],
  BUILD_FAILED: ["Build failed", "bad"],
  IMAGE_PUSHED: ["Image ready", "progress"],
  DEPLOYING: ["Rolling out", "progress"],
  RUNNING: ["Live", "good"],
  DEPLOY_FAILED: ["Rollout failed", "bad"],
  SUPERSEDED: ["Replaced", "idle"],
  // cluster states from /status
  NOT_DEPLOYED: ["Not deployed", "idle"],
  STOPPED: ["Stopped", "idle"],
  HEALTHY: ["Healthy", "good"],
  UPDATING: ["Updating", "progress"],
  UNHEALTHY: ["Unhealthy", "bad"],
};

export default function StatusBadge({ status }) {
  if (!status) return <span className="badge badge-idle">Never deployed</span>;
  const [label, tone] = LABELS[status] ?? [status, "idle"];
  return <span className={`badge badge-${tone}`}>{label}</span>;
}
