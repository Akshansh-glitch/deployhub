const test = require("node:test");
const assert = require("node:assert");
const http = require("node:http");
const { handler } = require("../app");

test("responds with greeting and version", async () => {
  const server = http.createServer(handler).listen(0);
  const { port } = server.address();
  const body = await (await fetch(`http://127.0.0.1:${port}/`)).json();
  server.close();
  assert.strictEqual(body.version, "v1");
  assert.match(body.message, /Hello/);
});

test("health endpoint returns ok", async () => {
  const server = http.createServer(handler).listen(0);
  const { port } = server.address();
  const res = await fetch(`http://127.0.0.1:${port}/healthz`);
  server.close();
  assert.strictEqual(res.status, 200);
});
