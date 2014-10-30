package com.github.davidhoyt.scalabot

import akka.actor.ActorRef
import com.github.davidhoyt.Bot
import com.typesafe.scalalogging.slf4j.LazyLogging


//TODO: Time out if something takes too long (infinite loop for example)

object ScalaBot {
  val supportedCommands = Seq(
    ":implicits",
    //":javap",
    //":kind",
    ":reset",
    ":toggleEcho",
    //":type",
    ":warnings",
    ":help",
    "?"
  )
}

class ScalaBot(enableExclamation: Boolean, announce: Boolean) extends Bot with LazyLogging {
  import com.github.davidhoyt.hipchat.HipChat._
  import com.github.davidhoyt.{BotMessage, BotInitialize, BotMessageReceived}
  import ScalaBot._

  var hipchat: Option[ActorRef] = None
  var room: Option[ActorRef] = None
  var roomId: String = _
  var repl: REPL = _
  var mentionName: String = _
  var toggleEcho = true

  val help: String =
    """Available commands:
      |  :implicits [-v]         show the implicits in scope
      |  :toggleEcho             toggle echoing submitted code with syntax highlighting
      |  :reset                  reset the repl to its initial state, forgetting all session entries
      |  :warnings               show the suppressed warnings from the most recent line which had any
    """.stripMargin.trim

  val unrecognizedCommand: String =
    "Unrecognized command.\n" + help

  def ltrim(value: String) =
    value.dropWhile(_ <= ' ')

  override def initializeReceived: InitializeReceived = {
    case BotInitialize(_, givenMentionName, givenRoomId, refHipchat, refRoom) =>
      hipchat = refHipchat
      room = refRoom
      roomId = givenRoomId
      mentionName = givenMentionName
      repl = new REPL(roomId)
      repl.start()
      if (announce) {
        logger.info(s"Announcing Scala bot for $roomId")
        hipchat map (_ ! StatusUpdate(Available, "BEEP WHIR GYVE"))
        room map (_ ! BotMessage("/me is reporting for duty! Enabling power mode... BEEP WHIR GYVE"))
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
      case Some(command) if command == ":toggleEcho" =>
        toggleEcho = !toggleEcho
        Seq(("/quote", if (toggleEcho) "Echoing is now enabled." else "Echoing is now disabled."))
      case Some(command) =>
        Seq(("/quote", repl.processCode(command)))
      case None if message.startsWith(":") =>
        Seq(("/quote", unrecognizedCommand))
      case None =>
        val echo =
          if (toggleEcho)
            Seq(("/code", message))
          else
            Seq()
        echo ++ Seq(("/quote", repl.processCode(message)))
    }

  def combine(messages: Seq[(String, String)]): Seq[BotMessage] =
    messages flatMap {
      case (prepend, reply) if !reply.trim().isEmpty =>
        Some(BotMessage(s"$prepend\n$reply"))
      case _ =>
        None
    }
}
