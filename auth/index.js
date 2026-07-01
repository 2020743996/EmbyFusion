const authRoutes = require("./routes");
const authMiddleware = require("./middleware");
const { signToken, verifyToken } = require("./jwt");
const { findUserByEmail, findUserById, createUser } = require("./db");

function mountAuth(app, prefix = "/auth") {
  app.use(prefix, authRoutes);
}

module.exports = {
  mountAuth,
  authMiddleware,
  signToken,
  verifyToken,
  findUserByEmail,
  findUserById,
  createUser,
};
