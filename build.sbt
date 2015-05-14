import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.SbtNativePackager.packageArchetype
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.archetypes.ServerLoader
import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._

version := "1.0.0-SNAPSHOT"

name := "hipchat-bot"

scalaVersion := "2.11.6"

val camelVersion = "2.14.0"
val akkaVersion = "2.3.11"

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  "org.igniterealtime.smack" % "smack-core" % "4.0.7",
  "org.igniterealtime.smack" % "smack-tcp" % "4.0.7",
  "org.igniterealtime.smack" % "smack-extensions" % "4.0.7",
  //
  "org.scala-lang" % "scala-compiler" % "2.11.6",
  "org.scala-lang" % "scala-reflect" % "2.11.6",
  //
  "org.wildfly.security" % "wildfly-security-manager" % "1.1.2.Final",
  "org.jboss.logging" % "jboss-logging" % "3.2.1.Final",
  //
  //"org.scala-lang.modules" %% "scala-xml" % "1.0.2",
  //"org.apache.camel" % "camel-xmpp" % camelVersion,
  //"org.apache.camel" % "camel-scala" % camelVersion,
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "io.reactivex" %% "rxscala" % "0.24.1",
  "com.typesafe" % "config" % "1.2.1",
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "org.scalatest" %% "scalatest" % "2.2.5" % "test",
  "net.ceedubs" %% "ficus" % "1.1.2",
  //Additional dependencies just to have them for use with the Scala bot.
  //
  "org.scalaz" %% "scalaz-core" % "7.1.2",
  "org.scalaz" %% "scalaz-effect" % "7.1.2",
  "org.scalaz" %% "scalaz-concurrent" % "7.1.2",
  "org.scalaz.stream" %% "scalaz-stream" % "0.7a",
  //
  "com.chuusai" %% "shapeless" % "2.1.0",
  //
  "org.typelevel" %% "shapeless-spire" % "0.3",
  "org.typelevel" %% "shapeless-scalaz" % "0.3",
  //
  "org.spire-math" %% "spire" % "0.9.1",
  //
  "org.scalacheck" %% "scalacheck" % "1.12.2"
)

packageArchetype.java_server

bashScriptConfigLocation := Some("${app_home}/../conf/jvmopts")

mappings in Universal += (file("scalabot-sandbox.policy"), "scalabot-sandbox.policy")
