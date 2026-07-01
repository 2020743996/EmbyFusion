const http = require("http");

const BASE = "http://localhost:3000";

function request(method, path, body) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE);
    const data = body ? JSON.stringify(body) : null;
    const req = http.request(
      {
        hostname: url.hostname,
        port: url.port,
        path: url.pathname,
        method,
        headers: {
          "Content-Type": "application/json",
          ...(body ? { "Content-Length": Buffer.byteLength(data) } : {}),
        },
      },
      (res) => {
        let chunk = "";
        res.on("data", (c) => (chunk += c));
        res.on("end", () => {
          try {
            resolve({ status: res.statusCode, body: JSON.parse(chunk) });
          } catch {
            resolve({ status: res.statusCode, body: chunk });
          }
        });
      }
    );
    req.on("error", reject);
    if (data) req.write(data);
    req.end();
  });
}

function requestAuth(method, path, token, body) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE);
    const data = body ? JSON.stringify(body) : null;
    const req = http.request(
      {
        hostname: url.hostname,
        port: url.port,
        path: url.pathname,
        method,
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
          ...(body ? { "Content-Length": Buffer.byteLength(data) } : {}),
        },
      },
      (res) => {
        let chunk = "";
        res.on("data", (c) => (chunk += c));
        res.on("end", () => {
          try {
            resolve({ status: res.statusCode, body: JSON.parse(chunk) });
          } catch {
            resolve({ status: res.statusCode, body: chunk });
          }
        });
      }
    );
    req.on("error", reject);
    if (data) req.write(data);
    req.end();
  });
}

async function runTests() {
  console.log("Running auth tests...\n");

  const email = `test-${Date.now()}@example.com`;
  const password = "testpass123";

  const reg = await request("POST", "/auth/register", {
    email,
    password,
    name: "Test User",
  });
  console.log("POST /auth/register:", reg.status);
  console.assert(reg.status === 201, "Expected 201");
  console.assert(reg.body.token, "Expected token");
  console.assert(reg.body.user.email === email, "Expected email match");

  const token = reg.body.token;

  const dup = await request("POST", "/auth/register", { email, password });
  console.log("POST /auth/register (dup):", dup.status);
  console.assert(dup.status === 409, "Expected 409 for duplicate");

  const login = await request("POST", "/auth/login", { email, password });
  console.log("POST /auth/login:", login.status);
  console.assert(login.status === 200, "Expected 200");
  console.assert(login.body.token, "Expected token");

  const badLogin = await request("POST", "/auth/login", {
    email,
    password: "wrong",
  });
  console.log("POST /auth/login (bad):", badLogin.status);
  console.assert(badLogin.status === 401, "Expected 401");

  const prot = await requestAuth("GET", "/protected", token);
  console.log("GET /protected:", prot.status);
  console.assert(prot.status === 200, "Expected 200");
  console.assert(prot.body.user.id, "Expected user id");

  const noAuth = await request("GET", "/protected");
  console.log("GET /protected (no token):", noAuth.status);
  console.assert(noAuth.status === 401, "Expected 401");

  console.log("\n✅ All tests passed");
}

module.exports = { runTests };

if (require.main === module) {
  runTests().catch((e) => {
    console.error("❌ Test failed:", e);
    process.exit(1);
  });
}
