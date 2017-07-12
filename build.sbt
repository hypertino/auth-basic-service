crossScalaVersions := Seq("2.12.1", "2.11.8")

scalaVersion in Global := "2.11.8"

lazy val `auth-service` = project in file(".") enablePlugins Raml2Hyperbus settings (
    name := "auth-basic-service",
    version := "0.1-SNAPSHOT",
    organization := "com.hypertino",  
    resolvers ++= Seq(
      Resolver.sonatypeRepo("public")
    ),
    libraryDependencies ++= Seq(
      "com.hypertino" %% "hyperbus" % "0.2-SNAPSHOT",
      "com.hypertino" %% "hyperbus-t-inproc" % "0.2-SNAPSHOT",
      "com.hypertino" %% "service-control" % "0.3-SNAPSHOT",
      "org.jasypt" % "jasypt" % "1.9.2",
      "com.hypertino" %% "service-config" % "0.2-SNAPSHOT" % "test",
      "ch.qos.logback" % "logback-classic" % "1.1.8" % "test",
      "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % "test",
      compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
    ),
    ramlHyperbusSources := Seq(
      ramlSource(
        path = "api/auth-basic-service-api/auth-basic.raml",
        packageName = "com.hypertino.api.authbasic",
        isResource = false
      ),
      ramlSource(
        path = "api/auth-basic-service-api/expects/user.raml",
        packageName = "com.hypertino.api.authbasic.expects.user",
        isResource = false
      )
    )
)
