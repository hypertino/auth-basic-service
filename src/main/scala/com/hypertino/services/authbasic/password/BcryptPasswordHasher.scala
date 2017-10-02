package com.hypertino.services.authbasic.password

import org.mindrot.jbcrypt.BCrypt

class BcryptPasswordHasher(rounds: Int = 10) extends PasswordHasher {
  override def checkPassword(candidate: String, hashed: String) = BCrypt.checkpw(candidate, hashed)
  override def encryptPassword(password: String) = BCrypt.hashpw(password, BCrypt.gensalt(rounds))
}
