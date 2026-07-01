const Database = require("better-sqlite3");
const path = require("path");

const DB_PATH = path.join(__dirname, "..", "data", "auth.db");

let db;

function getDb() {
  if (!db) {
    const fs = require("fs");
    fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });
    db = new Database(DB_PATH);
    db.pragma("journal_mode = WAL");
    db.exec(`
      CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        email TEXT UNIQUE NOT NULL,
        password TEXT NOT NULL,
        name TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
      )
    `);
  }
  return db;
}

function findUserByEmail(email) {
  return getDb().prepare("SELECT * FROM users WHERE email = ?").get(email);
}

function findUserById(id) {
  return getDb().prepare("SELECT * FROM users WHERE id = ?").get(id);
}

function createUser(email, password, name) {
  const stmt = getDb().prepare(
    "INSERT INTO users (email, password, name) VALUES (?, ?, ?)"
  );
  return stmt.run(email, password, name || null);
}

module.exports = { getDb, findUserByEmail, findUserById, createUser };
