import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.SbtNativePackager.packageArchetype
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.archetypes.ServerLoader
import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._

version := "1.0.0-SNAPSHOT"

name := "hipchat-bot"

scalaVersion := "2.11.4"

val camelVersion = "2.14.0"
val akkaVersion = "2.3.7"

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  "org.igniterealtime.smack" % "smack-core" % "4.0.5",
  "org.igniterealtime.smack" % "smack-tcp" % "4.0.5",
  "org.igniterealtime.smack" % "smack-extensions" % "4.0.5",
  //
  "org.scala-lang" % "scala-compiler" % "2.11.4",
  "org.scala-lang" % "scala-reflect" % "2.11.4",
  //
  "org.wildfly.security" % "wildfly-security-manager" % "1.0.1.Final",
  "org.jboss.logging" % "jboss-logging" % "3.1.3.GA",
  //
  //"org.scala-lang.modules" %% "scala-xml" % "1.0.2",
  //"org.apache.camel" % "camel-xmpp" % camelVersion,
  //"org.apache.camel" % "camel-scala" % camelVersion,
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "io.reactivex" %% "rxscala" % "0.23.0",
  "com.typesafe" % "config" % "1.2.1",
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "org.scalatest" %% "scalatest" % "2.2.2" % "test",
  "net.ceedubs" %% "ficus" % "1.1.1",
  //Additional dependencies just to have them for use with the Scala bot.
  //
  "org.scalaz" %% "scalaz-core" % "7.1.0",
  "org.scalaz" %% "scalaz-effect" % "7.1.0",
  "org.scalaz" %% "scalaz-concurrent" % "7.1.0",
  "org.scalaz.stream" %% "scalaz-stream" % "0.5a",
  //
  "com.chuusai" %% "shapeless" % "2.0.0",
  "org.typelevel" %% "shapeless-spire" % "0.3",
  "org.typelevel" %% "shapeless-scalaz" % "0.3",
  //
  "org.spire-math" %% "spire" % "0.8.2",
  //
  "org.scalacheck" %% "scalacheck" % "1.12.1"
)

packageArchetype.java_server

bashScriptConfigLocation := Some("${app_home}/../conf/jvmopts")

mappings in Universal += (file("scalabot-sandbox.policy"), "scalabox-sandbox.policy")
