package com.hypertino.services.authbasic

import java.util.Base64

import com.hypertino.api.authbasic.expects.user.UsersGet
import com.hypertino.api.authbasic.{EncryptedPassword, EncryptionsPost, ValidationResult, ValidationsPost}
import com.hypertino.binders.value.{Null, Obj}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{BadRequest, DynamicRequest, ErrorBody, HyperbusError, InternalServerError, Ok, RequestBase, ResponseBase, Unauthorized}
import com.hypertino.hyperbus.transport.api.CommandEvent
import com.hypertino.hyperbus.util.SeqGenerator
import com.hypertino.service.control.api.{Console, Service}
import monix.execution.Ack.Continue
import monix.execution.Scheduler
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}
import org.jasypt.util.password.StrongPasswordEncryptor

import scala.util.Success
import scala.util.control.NonFatal

class AuthBasicService(console: Console, implicit val injector: Injector) extends Service with Injectable {
  implicit val scheduler = inject[Scheduler]
  val hyperbus = inject[Hyperbus]
  val log = LoggerFactory.getLogger(getClass)
  log.info("AuthService started")

  // todo: support scheme configuration + backward compatibility?
  val passwordEncryptor = new StrongPasswordEncryptor

  val handlers = Seq (
    hyperbus.commands[ValidationsPost].subscribe { implicit command ⇒
      try {
        val authorization = command.request.body.authorization
        val spaceIndex = authorization.indexOf(" ")
        if (spaceIndex < 0 || authorization.substring(0, spaceIndex).compareToIgnoreCase("basic") != 0) {
          reportError(command, BadRequest(ErrorBody("format-error")))
        }
        else {
          val base64 = authorization.substring(spaceIndex+1)
          val userNameAndPassword = new String(Base64.getDecoder.decode(base64), "UTF-8")
          val semicolonIndex = userNameAndPassword.indexOf(":")
          if (semicolonIndex < 0) {
            reportError(command, BadRequest(ErrorBody("format-error-username")))
          }
          else {
            val userName = userNameAndPassword.substring(1, semicolonIndex)
            val password = userNameAndPassword.substring(semicolonIndex+1)

            // todo: configure lookup field
            val userNameFieldName = "email"
            hyperbus
              .ask(UsersGet(extraQuery=Obj.from(userNameFieldName → userName)))
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
                          Ok(ValidationResult(
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
              .runOnComplete(command.reply)
          }
        }
      }
      catch {
        case NonFatal(e) ⇒
          reportUnhandledError(command, e)
      }
      Continue
    },

    hyperbus.commands[EncryptionsPost].subscribe { implicit command ⇒
      try {
        command.reply(Success(
          Ok(EncryptedPassword(
            passwordEncryptor.encryptPassword(command.request.body.value)
          ))
        ))
      } catch {
        case NonFatal(e) ⇒
          reportUnhandledError(command, e)
      }
      Continue
    }
  )

  private def reportUnhandledError(implicit command: CommandEvent[RequestBase], e: Throwable) = reportError(command, InternalServerError(ErrorBody("unhandled-exception", Some(e.toString))), Some(e))
  private def reportError(command: CommandEvent[RequestBase], error: HyperbusError[ErrorBody], cause: Option[Throwable] = None ) = {
    cause.foreach { e ⇒
      log.error(s"${error.body}", e)
    }

    command.reply(Success(
      error
    ))
  }

  def stopService(controlBreak: Boolean): Unit = {
    handlers.foreach(_.cancel())
    log.info("AuthService stopped")
  }
}
