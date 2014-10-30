package com.github.davidhoyt.hipchat

import com.typesafe.config.Config
import com.typesafe.scalalogging.slf4j.LazyLogging

object Main extends App with LazyLogging {
  import akka.actor.ActorSystem
  import com.github.davidhoyt.BotFactory
  import com.github.davidhoyt.scalabot.ScalaBot
  import com.typesafe.config.ConfigFactory
  import net.ceedubs.ficus.Ficus._
  import HipChat._


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
  val rooms = configBots.as[Seq[String]]("scala-bot.rooms")

  val system = ActorSystem("xmpp")

  val hipchat = system.actorOf(HipChat.props, "HipChat")

  if (enabled) {
    hipchat ! Connect(HipChatConfiguration(host, port, userName, password))

    for (room <- rooms)
      hipchat ! JoinRoom(room, nickName, mentionName, BotFactory[ScalaBot], Seq(room, true /*enableExclamation*/, Some(hipchat)))
  }

  val scalaBot = BotFactory[ScalaBot].apply(Seq("console", false, None)).get

  println("Use :quit to exit the application.")
  print("scala> ")
  var line = ""
  while({line = Console.in.readLine(); line} != ":quit") {
    val (_, reply) = scalaBot.process(line)
    println(reply)
    print("scala> ")
  }

  system.shutdown()
  system.awaitTermination()

//
//  val chatEndpoint = s"xmpp://$userName@$host:$port?room=${rooms.head}&password=$password&nickname=$nickname"
//
////  val bot = ScalaBot(mentionName)
////  val main = new org.apache.camel.main.Main
////  main.addRouteBuilder(CamelBot(chatEndpoint, bot))
////  main.enableHangupSupport()
////  main.run(args)
}