scalaVersion := "2.12.11" // Also supports 2.11.x
organization := "org.pac4j"
version      := "1.0.0-SNAPSHOT"

val circeVersion = "0.13.0"
val http4sVersion = "0.21.6"
val pac4jVersion = "3.8.3"
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

scalacOptions ++= Seq("-Ypartial-unification", "-language:implicitConversions", "-language:higherKinds")
