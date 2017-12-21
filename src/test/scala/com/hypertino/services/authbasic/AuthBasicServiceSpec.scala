package com.hypertino.services.authbasic

import com.hypertino.authbasic.api._
import com.hypertino.authbasic.apiref.user.{User, UserCollection, UsersGet}
import com.hypertino.binders.value.{Null, Obj, Text}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{Created, ErrorBody, MessagingContext, Ok, ResponseBase, Unauthorized}
import com.hypertino.service.config.ConfigLoader
import com.hypertino.service.control.StdConsole
import com.hypertino.service.control.api.Console
import com.typesafe.config.Config
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.scalatest.time.{Millis, Span}
import scaldi.Module

import scala.concurrent.duration._
import com.hypertino.hyperbus.subscribe.Subscribable
import com.hypertino.hyperbus.transport.api.ServiceRegistrator
import com.hypertino.hyperbus.transport.registrators.DummyRegistrator

class AuthBasicServiceSpec extends FlatSpec with Module with BeforeAndAfterAll with ScalaFutures with Matchers with Subscribable {
  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(5000, Millis)))
  implicit val scheduler = monix.execution.Scheduler.Implicits.global
  implicit val mcx = MessagingContext.empty
  bind [Config] to ConfigLoader()
  bind [Scheduler] to scheduler
  bind [ServiceRegistrator] to DummyRegistrator
  bind [Hyperbus] to injected[Hyperbus]

  val hyperbus = inject[Hyperbus]
  val handlers = hyperbus.subscribe(this)
  var password1: Option[String] = None
  val service = new AuthBasicService()
  hyperbus.startServices()
  service.startService()

  def onUsersGet(implicit get: UsersGet) = Task.eval[ResponseBase] {
    val userId = get.headers.hrl.query.dynamic.email
    val p = password1
    password1 = None
    if (userId == Text("info@example.com")) {
      Ok(UserCollection(Seq(User(
        userId = "100500",
        password = p
      ))))
    } else {
      Ok(UserCollection(Seq.empty))
    }
  }

  override def afterAll() {
    service.stopService(false, 10.seconds).futureValue
    hyperbus.shutdown(10.seconds).runAsync.futureValue
  }

  "AuthBasicService" should "encrypt and validate password" in {
    //Thread.sleep(1000)

    val encryptedPassword = hyperbus
      .ask(EncryptionsPost(OriginalPassword("123456")))
      .runAsync
      .futureValue
      .body
      .value

    password1 = Some(encryptedPassword)

    val r = hyperbus
      .ask(ValidationsPost(Validation("Basic aW5mb0BleGFtcGxlLmNvbToxMjM0NTY="))) // info@example.com:123456
      .runAsync
      .futureValue

    r shouldBe a[Created[_]]
    r.body shouldBe ValidationResult(Obj.from("email" → "info@example.com", "user_id" → "100500"), Null)
  }

  "AuthBasicService" should "unathorize if user doesn't exists" in {
    val r = hyperbus
      .ask(ValidationsPost(Validation("Basic bWFtbW90aDoxMjM0NQ==")))
      .runAsync
      .failed
      .futureValue

    r shouldBe a[Unauthorized[_]]
    val b = r.asInstanceOf[Unauthorized[ErrorBody]].body
    b.code shouldBe "user_not_found"
  }

  "AuthBasicService" should "unathorize if password is incorrect" in {
    val encryptedPassword = hyperbus
      .ask(EncryptionsPost(OriginalPassword("123456")))
      .runAsync
      .futureValue
      .body
      .value

    password1 = Some(encryptedPassword)

    val r = hyperbus
      .ask(ValidationsPost(Validation("Basic aW5mb0BleGFtcGxlLmNvbTo2NTQzMjE="))) // info@example.com:654321
      .runAsync
      .failed
      .futureValue

    r shouldBe a[Unauthorized[_]]
    val b = r.asInstanceOf[Unauthorized[ErrorBody]].body
    b.code shouldBe "password_is_not_valid"
  }
}
