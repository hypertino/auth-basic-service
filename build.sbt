crossScalaVersions := Seq("2.12.3", "2.11.11")

scalaVersion := crossScalaVersions.value.head

lazy val `auth-service` = project in file(".") enablePlugins Raml2Hyperbus settings (
    name := "auth-basic-service",
    version := "0.2-SNAPSHOT",
    organization := "com.hypertino",  
    resolvers ++= Seq(
      Resolver.sonatypeRepo("public")
    ),
    libraryDependencies ++= Seq(
      "com.hypertino" %% "hyperbus" % "0.3-SNAPSHOT",
      "com.hypertino" %% "hyperbus-t-inproc" % "0.3-SNAPSHOT",
      "com.hypertino" %% "service-control" % "0.3.0",
      "org.mindrot" % "jbcrypt" % "0.4",
      "com.hypertino" %% "service-config" % "0.2.0" % "test",
      "ch.qos.logback" % "logback-classic" % "1.2.3" % "test",
      "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % "test",
      compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
    ),
    ramlHyperbusSources := Seq(
      ramlSource(
        path = "api/auth-basic-service-api/auth-basic.raml",
        packageName = "com.hypertino.authbasic.api",
        isResource = false
      ),
      ramlSource(
        path = "api/auth-basic-service-api/expects/user.raml",
        packageName = "com.hypertino.authbasic.apiref.user",
        isResource = false
      )
    )
)
