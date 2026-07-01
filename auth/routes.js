const express = require("express");
const bcrypt = require("bcrypt");
const { findUserByEmail, createUser } = require("./db");
const { signToken } = require("./jwt");

const router = express.Router();
const SALT_ROUNDS = 10;

router.post("/register", async (req, res) => {
  try {
    const { email, password, name } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: "Email and password are required" });
    }

    const existing = findUserByEmail(email);
    if (existing) {
      return res.status(409).json({ error: "Email already registered" });
    }

    const hashed = await bcrypt.hash(password, SALT_ROUNDS);
    const result = createUser(email, hashed, name);

    const token = signToken({ userId: result.lastInsertRowid, email });

    res.status(201).json({
      user: { id: result.lastInsertRowid, email, name },
      token,
    });
  } catch (error) {
    console.error("[register] Error:", error.message);
    res.status(500).json({ error: "Internal server error" });
  }
});

router.post("/login", async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: "Email and password are required" });
    }

    const user = findUserByEmail(email);
    if (!user) {
      return res.status(401).json({ error: "Invalid credentials" });
    }

    const valid = await bcrypt.compare(password, user.password);
    if (!valid) {
      return res.status(401).json({ error: "Invalid credentials" });
    }

    const token = signToken({ userId: user.id, email: user.email });

    res.json({
      user: { id: user.id, email: user.email, name: user.name },
      token,
    });
  } catch (error) {
    console.error("[login] Error:", error.message);
    res.status(500).json({ error: "Internal server error" });
  }
});

module.exports = router;
