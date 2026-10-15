import socket
from datetime import datetime, timezone

from flask import Flask, jsonify

VERSION = "v1"  # change and redeploy to demo a rolling update

app = Flask(__name__)


@app.get("/")
def hello():
    return jsonify(
        message="Hello from DeployHub (Flask)!",
        version=VERSION,
        pod=socket.gethostname(),
        time=datetime.now(timezone.utc).isoformat(),
    )


@app.get("/healthz")
def health():
    return "ok"
