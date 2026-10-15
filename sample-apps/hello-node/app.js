const os = require("node:os");

const VERSION = "v1"; // change and redeploy to demo a rolling update

function handler(req, res) {
  if (req.url === "/healthz") {
    res.writeHead(200, { "Content-Type": "text/plain" });
    return res.end("ok");
  }
  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(JSON.stringify({
    message: "Hello from DeployHub (Node.js)!",
    version: VERSION,
    pod: os.hostname(),
    time: new Date().toISOString(),
  }));
}

module.exports = { handler, VERSION };
