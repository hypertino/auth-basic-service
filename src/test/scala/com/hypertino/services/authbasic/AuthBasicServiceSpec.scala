package com.hypertino.services.authbasic

import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.transport.api.ServiceResolver
import com.hypertino.hyperbus.transport.resolvers.{PlainEndpoint, PlainResolver}
import com.hypertino.service.control.{ConsoleServiceController, RuntimeShutdownMonitor, StdConsole}
import com.hypertino.service.control.api.{Console, Service, ServiceController, ShutdownMonitor}
import com.typesafe.config.Config
import monix.execution.Scheduler
import org.scalatest.FlatSpec
import scaldi.Module

class AuthBasicServiceSpec extends FlatSpec with Module {
  bind [Config] to ConfigLoader()
  bind [Scheduler] identifiedBy 'scheduler to monix.execution.Scheduler.Implicits.global
  bind [Hyperbus] identifiedBy 'hyperbus to injected[Hyperbus]
  bind [Console]                identifiedBy 'console              toNonLazy injected[StdConsole]
  bind [ServiceController]      identifiedBy 'serviceController    toNonLazy injected[ConsoleServiceController]
  bind [ShutdownMonitor]        identifiedBy 'shutdownMonitor      toNonLazy injected[RuntimeShutdownMonitor]
  bind [Service] to injected [ExampleService]


  "AuthBasicService" should "encrypt and validate password" in {

  }
}
