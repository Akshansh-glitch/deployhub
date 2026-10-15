import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

function Chart({ title, unit, points }) {
  const data = (points ?? []).map((p) => ({ t: p.t * 1000, v: p.v }));
  const last = data.length ? data[data.length - 1].v : null;
  return (
    <figure className="chart">
      <figcaption>
        <span>{title}</span>
        <strong>{last === null ? "–" : `${last} ${unit}`}</strong>
      </figcaption>
      <div className="chart-body">
        {data.length === 0 ? (
          <p className="chart-empty">No data yet</p>
        ) : (
          <ResponsiveContainer width="100%" height={110}>
            <AreaChart data={data} margin={{ top: 4, right: 0, bottom: 0, left: 0 }}>
              <XAxis dataKey="t" type="number" domain={["dataMin", "dataMax"]} hide />
              <YAxis hide domain={[0, "auto"]} />
              <Tooltip
                labelFormatter={(t) => new Date(t).toLocaleTimeString()}
                formatter={(v) => [`${v} ${unit}`, title]}
                contentStyle={{ background: "var(--surface)", border: "1px solid var(--line)", borderRadius: 6 }}
              />
              <Area
                type="monotone"
                dataKey="v"
                stroke="var(--harbour)"
                fill="var(--harbour-soft)"
                strokeWidth={1.5}
                isAnimationActive={false}
              />
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>
    </figure>
  );
}

export default function MetricsCharts({ metrics, grafanaUrl, showRequests }) {
  if (!metrics) return <p className="muted">Loading metrics…</p>;
  if (!metrics.available) {
    return (
      <p className="notice">
        Metrics are unavailable because Prometheus can't be reached. Run <code>scripts/port-forward.sh</code> and keep
        it open.
      </p>
    );
  }
  return (
    <>
      <div className="charts">
        <Chart title="CPU" unit="mCPU" points={metrics.cpuMillicores} />
        <Chart title="Memory" unit="MiB" points={metrics.memoryMiB} />
        <Chart title="Pods available" unit="" points={metrics.podsAvailable} />
        {showRequests && <Chart title="Requests" unit="/s" points={metrics.requestsPerSecond} />}
      </div>
      {grafanaUrl && (
        <p className="muted small">
          Last 15 minutes from Prometheus.{" "}
          <a href={`${grafanaUrl}/d/deployhub-apps`} target="_blank" rel="noreferrer">
            Open the full dashboard in Grafana
          </a>
        </p>
      )}
    </>
  );
}
