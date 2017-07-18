package com.hypertino.services.authbasic

import java.util.Base64

import com.hypertino.authbasic.api.{EncryptedPassword, EncryptionsPost, ValidationResult, ValidationsPost}
import com.hypertino.authbasic.apiref.user.UsersGet
import com.hypertino.binders.value.{Null, Obj}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{BadRequest, Created, ErrorBody, Ok, ResponseBase, Unauthorized}
import com.hypertino.service.control.api.Service
import monix.eval.Task
import monix.execution.Scheduler
import org.jasypt.util.password.StrongPasswordEncryptor
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

class AuthBasicService(implicit val injector: Injector) extends Service with Injectable {
  private implicit val scheduler = inject[Scheduler]
  private val hyperbus = inject[Hyperbus]
  private val log = LoggerFactory.getLogger(getClass)
  log.info("AuthService started")

  // todo: support scheme configuration + backward compatibility?
  private val passwordEncryptor = new StrongPasswordEncryptor
  private val handlers = hyperbus.subscribe(this, log)

  def onValidationsPost(implicit post: ValidationsPost): Task[ResponseBase] = {
    val authorization = post.body.authorization
    val spaceIndex = authorization.indexOf(" ")
    if (spaceIndex < 0 || authorization.substring(0, spaceIndex).compareToIgnoreCase("basic") != 0) {
      Task.eval(BadRequest(ErrorBody("format-error")))
    }
    else {
      val base64 = authorization.substring(spaceIndex + 1)
      val userNameAndPassword = new String(Base64.getDecoder.decode(base64), "UTF-8")
      val semicolonIndex = userNameAndPassword.indexOf(":")
      if (semicolonIndex < 0) {
        Task.eval(BadRequest(ErrorBody("format-error-username")))
      }
      else {
        val userName = userNameAndPassword.substring(0, semicolonIndex)
        val password = userNameAndPassword.substring(semicolonIndex + 1)

        // todo: configure lookup field
        val userNameFieldName = "email"
        hyperbus
          .ask(UsersGet(fields=Some(Seq("user_id", "password")), // todo: get from body companion?
            $query = Obj.from(userNameFieldName → userName)))
          .map {
            case Ok(users, _) ⇒ {
              val r: ResponseBase =
                if (users.items.isEmpty || users.items.tail.nonEmpty) {
                  Unauthorized(ErrorBody("user-not-found", Some(s"User '$userName' is not found")))
                }
                else {
                  val user = users.items.head
                  if (user.password.isEmpty) {
                    Unauthorized(ErrorBody("password-is-not-defined", Some(s"Password of user '$userName' is not defined")))
                  }
                  else {
                    if (passwordEncryptor.checkPassword(password, user.password.get)) {
                      Created(ValidationResult(
                        identityKeys = Obj.from(
                          userNameFieldName → userName,
                          "user_id" → user.userId
                        ),
                        extra = Null
                      ))
                    }
                    else {
                      Unauthorized(ErrorBody("password-is-not-valid", Some(s"Password of user '$userName' is not valid")))
                    }
                  }
                }
              r
            }
          }
      }
    }
  }

  def onEncryptionsPost(implicit post: EncryptionsPost): Task[ResponseBase] = Task.eval {
    Created(EncryptedPassword(
      passwordEncryptor.encryptPassword(post.body.value)
    ))
  }

  def stopService(controlBreak: Boolean): Unit = {
    handlers.foreach(_.cancel())
    log.info("AuthService stopped")
  }
}
