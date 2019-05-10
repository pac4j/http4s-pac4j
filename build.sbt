scalaVersion := "2.12.7" // Also supports 2.11.x

val circeVersion = "0.11.1"
val http4sVersion = "0.16.6a"
val pac4jVersion = "3.6.1"

// Only necessary for SNAPSHOT releases
resolvers += Resolver.sonatypeRepo("snapshots")
resolvers += "opensaml Repository" at "https://build.shibboleth.net/nexus/content/repositories/releases"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-jawn" % circeVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % "0.16.5a",
  "org.http4s" %% "http4s-blaze-client" % "0.16.5a",
  "org.pac4j" % "pac4j-core" % pac4jVersion,
  "org.pac4j" % "pac4j-cas" % pac4jVersion,
  "org.pac4j" % "pac4j-http" % pac4jVersion,
  "org.pac4j" % "pac4j-jwt" % pac4jVersion,
  "org.pac4j" % "pac4j-oauth" % pac4jVersion,
  "org.pac4j" % "pac4j-oidc" % pac4jVersion,
  "org.pac4j" % "pac4j-openid" % pac4jVersion,
  "org.pac4j" % "pac4j-saml" % pac4jVersion,
  "com.lihaoyi" %% "scalatags" % "0.6.7"
)

scalacOptions ++= Seq("-Ypartial-unification", "-language:implicitConversions", "-language:higherKinds")
