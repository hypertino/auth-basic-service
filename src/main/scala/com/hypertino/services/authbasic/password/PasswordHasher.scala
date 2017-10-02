package com.hypertino.services.authbasic.password

trait PasswordHasher {
  def checkPassword(candidate: String, hash: String): Boolean
  def encryptPassword(password: String): String
}
