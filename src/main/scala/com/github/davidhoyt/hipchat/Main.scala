package com.github.davidhoyt.hipchat

import com.typesafe.scalalogging.slf4j.LazyLogging

object Main extends App with LazyLogging {
  import akka.pattern.ask
  import akka.actor.ActorSystem
  import com.github.davidhoyt._
  import com.github.davidhoyt.scalabot.ScalaBot
  import com.typesafe.config.{Config, ConfigFactory}
  import java.io.File
  import net.ceedubs.ficus.Ficus._
  import scala.concurrent.{Await, Future}
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global
  //import scala.tools.nsc.util.ClassPath
  import HipChat._
  import Sandbox._

  @volatile var running = true

  implicit val timeout = akka.util.Timeout(30.seconds)

  val system = ActorSystem("xmpp")
  sys.addShutdownHook {
    running = false
  }

  //ClassPath.split(sys.props("java.class.path")).sorted foreach println

  sys.props("application.home") = new File(".").getAbsolutePath

  ThreadPrintStream.replaceSystemOut()

  Sandbox.install()

  val environment: String =
    sys.env.get("ENV")
      .orElse(sys.props.get("env"))
      .getOrElse("dev")

  val configRoot = ConfigFactory.load()

  val config =
    configRoot
      .getConfig(environment)
      .withFallback(configRoot)

  val hipchat = system.actorOf(HipChat.props, "HipChat")

  val configBots = config.as[Config]("bots")

  val host = config.as[String]("hipchat.host")
  val port = config.as[Int]("hipchat.port")

  val enableStdinBot = configBots.as[Boolean]("enable-stdin-bot")

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

  def launchScalaBots(): Future[Boolean] = {
    if (enabled) {
      hipchat ! Connect(HipChatConfiguration(host, port, userName, password))

      val result =
        for (room <- rooms)
          yield hipchat ? JoinRoom(room, nickName, mentionName, BotFactory[ScalaBot], Seq(true /*enableExclamation*/ , true /*announce*/ , maxLines, maxOutputLength, maxRunningTime, supportedDependencies, blacklist, runOnStartup))

      Future
        .sequence(result)
        .map(_.asInstanceOf[Seq[RoomJoinedComplete]].forall(_.success))
    } else {
      Future
        .successful(true)
    }
  }

  val waitForAllBotsToInitialize =
    Future.sequence(Seq(
      launchScalaBots()
    )).map(_.forall(_ == true))

  if (!Await.result(waitForAllBotsToInitialize, 60.seconds)) {
    sys.error(s"Unable to load all bots in the time specified.")
    sys.exit(1)
  }

  if (enableStdinBot) {
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
    sys.exit(0)
  }

  while(running) {
    Thread.sleep(300L)
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