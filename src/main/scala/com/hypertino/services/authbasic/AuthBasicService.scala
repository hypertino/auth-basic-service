package com.hypertino.services.authbasic

import java.util.Base64

import com.hypertino.authbasic.api.{EncryptedPassword, EncryptionsPost, ValidationResult, ValidationsPost}
import com.hypertino.authbasic.apiref.user.UsersGet
import com.hypertino.binders.value.{Null, Obj}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{BadRequest, Created, ErrorBody, Ok, ResponseBase, Unauthorized}
import com.hypertino.hyperbus.subscribe.Subscribable
import com.hypertino.service.control.api.Service
import com.hypertino.services.authbasic.password.{BcryptPasswordHasher, PasswordHasher}
import com.hypertino.services.authbasic.util.ErrorCode
import monix.eval.Task
import monix.execution.Scheduler
import com.typesafe.scalalogging.StrictLogging
import scaldi.{Injectable, Injector}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class AuthBasicService(implicit val injector: Injector) extends Service with Injectable with Subscribable with StrictLogging {
  private implicit val scheduler = inject[Scheduler]
  private val hyperbus = inject[Hyperbus]

  protected val passwordHasher: PasswordHasher = new BcryptPasswordHasher()
  private val handlers = hyperbus.subscribe(this, logger)

  logger.info(s"${getClass.getName} is INITIALIZED")

  override def startService(): Unit = {
    logger.info(s"${getClass.getName} is STARTED")
  }

  def onValidationsPost(implicit post: ValidationsPost): Task[ResponseBase] = {
    val authorization = post.body.authorization
    val spaceIndex = authorization.indexOf(" ")
    if (spaceIndex < 0 || authorization.substring(0, spaceIndex).compareToIgnoreCase("basic") != 0) {
      Task.eval(BadRequest(ErrorBody(ErrorCode.FORMAT_ERROR)))
    }
    else {
      val base64 = authorization.substring(spaceIndex + 1)
      val userNameAndPassword = new String(Base64.getDecoder.decode(base64), "UTF-8")
      val semicolonIndex = userNameAndPassword.indexOf(":")
      if (semicolonIndex < 0) {
        Task.eval(BadRequest(ErrorBody(ErrorCode.FORMAT_ERROR_USERNAME)))
      }
      else {
        val userName = userNameAndPassword.substring(0, semicolonIndex)
        val password = userNameAndPassword.substring(semicolonIndex + 1)

        // todo: configure lookup field
        val userNameFieldName = "email"
        hyperbus
          .ask(UsersGet(fields=Some(Seq("user_id", "password")), // todo: get from body companion?
            query = Obj.from(userNameFieldName → userName)))
          .map {
            case Ok(users, _) ⇒ {
              val r: ResponseBase =
                if (users.items.isEmpty || users.items.tail.nonEmpty) {
                  Unauthorized(ErrorBody(ErrorCode.USER_NOT_FOUND, Some(s"User '$userName' is not found")))
                }
                else {
                  val user = users.items.head
                  if (user.password.isEmpty) {
                    Unauthorized(ErrorBody(ErrorCode.PASSWORD_IS_NOT_DEFINED, Some(s"Password of user '$userName' is not defined")))
                  }
                  else {
                    if (passwordHasher.checkPassword(password, user.password.get)) {
                      Created(ValidationResult(
                        identityKeys = Obj.from(
                          userNameFieldName → userName,
                          "user_id" → user.userId
                        ),
                        extra = Null
                      ))
                    }
                    else {
                      Unauthorized(ErrorBody(ErrorCode.PASSWORD_IS_NOT_VALID, Some(s"Password of user '$userName' is not valid")))
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
      passwordHasher.encryptPassword(post.body.value)
    ))
  }

  override def stopService(controlBreak: Boolean, timeout: FiniteDuration): Future[Unit] = Future {
    handlers.foreach(_.cancel())
    logger.info(s"${getClass.getName} is STOPPED")
  }
}
