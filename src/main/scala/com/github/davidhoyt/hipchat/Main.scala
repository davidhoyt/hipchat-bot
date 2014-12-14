package com.github.davidhoyt.hipchat

import java.io.File

import com.github.davidhoyt.{ThreadPrintStream, Security, BotInitialize}
import com.typesafe.config.Config
import com.typesafe.scalalogging.slf4j.LazyLogging

import scala.concurrent.duration.FiniteDuration
import scala.tools.nsc.util.ClassPath

object Main extends App with LazyLogging {
  import akka.actor.ActorSystem
  import com.github.davidhoyt.BotFactory
  import com.github.davidhoyt.scalabot.ScalaBot
  import com.typesafe.config.ConfigFactory
  import net.ceedubs.ficus.Ficus._
  import HipChat._

  //ClassPath.split(sys.props("java.class.path")).sorted foreach println

  sys.props("application.home") = new File(".").getAbsolutePath
  sys.props("java.security.policy") = new File("hipchat.policy").getAbsolutePath

  ThreadPrintStream.replaceSystemOut()
  Security.install()
  Security.privileged {

    val configRoot = ConfigFactory.load()

    val environment: String =
      sys.env.get("ENV")
        .orElse(sys.props.get("env"))
        .getOrElse("dev")

    val config =
      configRoot
        .getConfig(environment)
        .withFallback(configRoot)

    val configBots = config.as[Config]("bots")

    val host = config.as[String]("hipchat.host")
    val port = config.as[Int]("hipchat.port")

    val nickName = configBots.as[String]("scala-bot.nickname")
    val mentionName = configBots.as[String]("scala-bot.mention-name")
    val userName = configBots.as[String]("scala-bot.username")
    val password = configBots.as[String]("scala-bot.password")
    val enabled = configBots.as[Boolean]("scala-bot.enabled")
    val rooms = configBots.as[String]("scala-bot.rooms").split(',').map(_.trim).toSeq
    val supportedDependencies = configBots.as[Seq[String]]("scala-bot.supported-dependencies")
    val blacklist = configBots.as[Seq[String]]("scala-bot.blacklist")
    val maxLines = configBots.as[Int]("scala-bot.max-lines")
    val maxOutputLength = configBots.as[Int]("scala-bot.max-output-length")
    val maxRunningTime = configBots.as[FiniteDuration]("scala-bot.max-running-time")
    val runOnStartup = configBots.as[String]("scala-bot.run-on-startup")

    val system = ActorSystem("xmpp")

    val hipchat = system.actorOf(HipChat.props, "HipChat")

    if (enabled) {
      hipchat ! Connect(HipChatConfiguration(host, port, userName, password))

      for (room <- rooms)
        hipchat ! JoinRoom(room, nickName, mentionName, BotFactory[ScalaBot], Seq(true /*enableExclamation*/, true /*announce*/, maxLines, maxOutputLength, maxRunningTime, supportedDependencies, blacklist, runOnStartup))
    }

    //Run a Scala bot on stdin for local testing purposes.
    val scalaBot = BotFactory[ScalaBot].apply(Seq(false, false, maxLines, maxOutputLength, maxRunningTime, supportedDependencies, blacklist, runOnStartup)).get
    scalaBot.initializeReceived(BotInitialize("", "", "console", None, None))

    println("Use :quit to exit the application.")
    print("scala> ")
    var line = ""
    try {
      while(({line = Console.in.readLine(); line} ne null) && line != ":quit") {
        val (_, reply) = scalaBot.process(line).last
        println(reply)
        print("scala> ")
      }
    } catch {
      case t: Throwable =>
        t.printStackTrace()
    }

    system.shutdown()
    system.awaitTermination()
  }


//
//  val chatEndpoint = s"xmpp://$userName@$host:$port?room=${rooms.head}&password=$password&nickname=$nickname"
//
////  val bot = ScalaBot(mentionName)
////  val main = new org.apache.camel.main.Main
////  main.addRouteBuilder(CamelBot(chatEndpoint, bot))
////  main.enableHangupSupport()
////  main.run(args)
}