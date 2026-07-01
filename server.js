const express = require("express");
const { mountAuth, authMiddleware } = require("./auth");

const app = express();
app.use(express.json());

mountAuth(app);

app.get("/protected", authMiddleware, (req, res) => {
  res.json({ message: "Access granted", user: req.user });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Auth server running on http://localhost:${PORT}`);
  console.log("Endpoints:");
  console.log("  POST /auth/register - { email, password, name }");
  console.log("  POST /auth/login    - { email, password }");
  console.log("  GET  /protected     - Bearer <token>");
});
