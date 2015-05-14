package com.github.davidhoyt.scalabot

import akka.actor.ActorRef
import com.github.davidhoyt.Bot
import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.duration.FiniteDuration

object ScalaBot {
  val supportedCommands = Seq(
    ":dependencies",
    //":javap",
    ":kind",
    ":reset",
    ":toggleEcho",
    ":toggleQuote",
    ":type",
    ":warnings",
    ":help",
    "?"
  )
}

class ScalaBot(enableExclamation: Boolean, announce: Boolean, maxLines: Int, maxOutputLength: Int, maxRunningTime: FiniteDuration, supportedDependencies: Seq[String], blacklist: Seq[String], runOnStartup: String) extends Bot {
  import com.github.davidhoyt.hipchat.HipChat._
  import com.github.davidhoyt.{BotMessage, BotInitialize, BotMessageReceived}
  import ScalaBot._

  var hipchat: Option[ActorRef] = None
  var room: Option[ActorRef] = None
  var roomId: String = _
  var repl: REPL = _
  var mentionName: String = _
  var toggleEcho = false
  var toggleQuote = true

  val help: String =
    """Available commands:
      |  :dependencies       displays the list of supported dependencies for this bot
      |  :kind               display the kind of expression's type
      |  :toggleEcho         toggle echoing submitted code with syntax highlighting
      |  :toggleQuote        toggle quoting the output
      |  :reset              reset the repl to its initial state, forgetting all session entries
      |  :type               display the type of an expression without evaluating it
      |  :warnings           show the suppressed warnings from the most recent line which had any
    """.stripMargin.trim

  val unrecognizedCommand: String =
    "Unrecognized command.\n" + help

  val dependencies: String =
    s"""Supported dependencies:
       |  ${supportedDependencies.mkString("\n  ")}
     """.stripMargin

  def ltrim(value: String) =
    value.dropWhile(_ <= ' ')

  override def initializeReceived: InitializeReceived = {
    case BotInitialize(_, givenMentionName, givenRoomId, refHipchat, refRoom) =>
      hipchat = refHipchat
      room = refRoom
      roomId = givenRoomId
      mentionName = givenMentionName
      repl = new REPL(roomId, maxLines, maxOutputLength, maxRunningTime, blacklist, runOnStartup)
      repl.start()
      if (announce) {
        logger.info(s"Announcing Scala bot for $roomId")
        hipchat foreach (_ ! StatusUpdate(Available, "BEEP WHIR GYVE"))
        room foreach (_ ! BotMessage("/me is reporting for duty! Enabling power mode... BEEP WHIR GYVE"))
      }
      logger.info(s"Scala bot for $roomId initialized")
  }

  override def messageReceived: MessageReceived = {
    case BotMessageReceived(_, message) if message.startsWith(s"@$mentionName") && message.charAt(mentionName.length + 1) <= ' ' =>
      combine(process(ltrim(message.drop(mentionName.length + 1))))

    case BotMessageReceived(_, message) if enableExclamation && message.startsWith("!") =>
      combine(process(ltrim(message.drop(1))))
  }

  def process(message: String): Seq[(String, String)] =
    supportedCommands find message.startsWith match {
      case Some(command) if command == ":help" || command == "?" =>
        Seq(("/quote", help))
      case Some(command) if command == ":dependencies" =>
        Seq(("/quote", dependencies))
      case Some(command) if command == ":toggleEcho" =>
        toggleEcho = !toggleEcho
        Seq(("/quote", if (toggleEcho) "Echoing is now enabled." else "Echoing is now disabled."))
      case Some(command) if command == ":toggleQuote" =>
        toggleQuote = !toggleQuote
        Seq(("/quote", if (toggleQuote) "Quoting is now enabled." else "Quoting is now disabled."))
      case Some(command) =>
        Seq(("/quote", repl.processCode(message)))
      case None if message.startsWith(":") =>
        Seq(("/quote", unrecognizedCommand))
      case None =>
        val echo =
          if (toggleEcho)
            Seq(("/code", message))
          else
            Seq()
        val prepend = if (toggleQuote) "/quote" else ""
        echo ++ Seq((prepend, repl.processCode(message)))
    }

  def combine(messages: Seq[(String, String)]): Seq[BotMessage] =
    messages flatMap {
      case (prepend, reply) if !reply.trim().isEmpty =>
        Some(BotMessage(if (prepend.nonEmpty) s"$prepend\n$reply" else s"$reply"))
      case _ =>
        None
    }
}
