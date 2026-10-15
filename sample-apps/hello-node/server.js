const http = require("node:http");
const { handler } = require("./app");

const port = Number(process.env.PORT) || 3000;
http.createServer(handler).listen(port, () => console.log(`hello-node listening on ${port}`));
