crossScalaVersions := Seq("2.12.18", "2.13.12", "3.3.1")
organization := "org.pac4j"
version      := "4.2.1-SNAPSHOT"

val circeVersion = "0.14.6"
val http4sVersion = "0.23.25"
val pac4jVersion = "5.7.2"
val specs2Version = "4.20.3"
val catsVersion = "2.10.0"
val vaultVersion = "3.5.0"
val mouseVersion = "1.2.2"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-jawn" % circeVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-server" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.pac4j" % "pac4j-core" % pac4jVersion,
  "org.slf4j" % "slf4j-api" % "2.0.10",
  "commons-codec" % "commons-codec" % "1.16.0",
  "org.typelevel" %% "cats-core" % catsVersion,
  "org.typelevel" %% "vault" % vaultVersion,
  "org.typelevel" %% "mouse" % mouseVersion,
  "org.scala-lang.modules" %% "scala-collection-compat" % "2.11.0",
)

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-jawn" % http4sVersion % Test,
  "org.specs2" %% "specs2-matcher-extra" % specs2Version % Test,
  "org.specs2" %% "specs2-scalacheck" % specs2Version % Test,
  "org.specs2" %% "specs2-cats" % specs2Version % Test,
)

libraryDependencies ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Seq(
      "io.circe" %% "circe-optics" % "0.14.1" % Test,
    )
    case _ => Seq(
      "io.circe" %% "circe-optics" % "0.15.0" % Test,
    )
  }
}

val username = sys.env.get("SONATYPE_USERNAME").getOrElse("")
val password = sys.env.get("SONATYPE_PASSWORD").getOrElse("")
credentials += Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)

homepage := Some(url("https://github.com/pac4j/http4s-pac4j"))
licenses := List("Apache 2" -> new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/pac4j/http4s-pac4j"),
    "scm:git@github.com:pac4j/http4s-pac4j.git"
  )
)
developers := List(
  Developer(
    id    = "leleuj",
    name  = "Jerome LELEU",
    email = "leleuj@gmail.com",
    url   = url("https://github.com/leleuj")
  )
)

pomIncludeRepository := { _ => false }
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
publishMavenStyle := true

scalacOptions ++= {
  val scalaVersion0 = scalaVersion.value
  val partialUnification =
    if (scalaVersion0.startsWith("2.12")) {
      Seq("-Ypartial-unification")
    } else Seq()

  partialUnification ++ Seq(
    "-deprecation",
    "-language:implicitConversions",
    "-language:higherKinds"
  )
}
