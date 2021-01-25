crossScalaVersions := Seq("2.12.12", "2.13.3")
organization := "org.pac4j"
version      := "2.0.0"

val circeVersion = "0.13.0"
val http4sVersion = "0.21.6+"
val pac4jVersion = "3.9.0"
val specs2Version = "4.10.0"
val catsVersion = "2.1.1"
val catsEffectVersion = "2.1.3"
val vaultVersion = "2.0.0"
val mouseVersion = "0.25"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-jawn" % circeVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-server" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.pac4j" % "pac4j-core" % pac4jVersion,
  "org.slf4j" % "slf4j-api" % "1.7.26",
  "commons-codec" % "commons-codec" % "1.14",
  "org.typelevel" %% "cats-core" % catsVersion,
  "io.chrisdavenport" %% "vault" % vaultVersion,
  "org.typelevel" %% "mouse" % mouseVersion,

  "io.circe" %% "circe-optics" % circeVersion % Test,
  "org.http4s" %% "http4s-jawn" % http4sVersion % Test,
  "org.specs2" %% "specs2-matcher-extra" % specs2Version % Test,
  "org.specs2" %% "specs2-scalacheck" % specs2Version % Test,
  "org.specs2" %% "specs2-cats" % specs2Version,

)

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

homepage := Some(url("https://github.com/pac4j/http4s-pac4j"))
licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
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
    "-language:implicitConversions",
    "-language:higherKinds"
  )
}
