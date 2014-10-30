package com.github.davidhoyt.scalabot

import com.github.davidhoyt.Bot


//TODO: Time out if something takes too long (infinite loop for example)

object ScalaBot {
  val supportedCommands = Seq(
    ":implicits",
    //":javap",
    //":kind",
    ":reset",
    //":type",
    ":warnings",
    ":help",
    "?"
  )
}

class ScalaBot(roomId: String, enableExclamation: Boolean) extends Bot {
  import com.github.davidhoyt.{BotConfiguration, BotMessage}
  import ScalaBot._

  val repl = new REPL(roomId)
  repl.start()

  val help: String =
    """Available commands:
      |  :implicits [-v]         show the implicits in scope
      |  :reset                  reset the repl to its initial state, forgetting all session entries
      |  :warnings               show the suppressed warnings from the most recent line which had any
    """.stripMargin.trim

  val unrecognizedCommand: String =
    "Unrecognized command.\n" + help

  def ltrim(value: String) =
    value.dropWhile(_ <= ' ')

  override def messageReceived: MessageReceived = {
    case BotMessage(BotConfiguration(_, mentionName, _, _), _, message) if message.startsWith(s"@$mentionName") =>
      val (prepend, reply) = process(ltrim(message.drop(mentionName.length + 1)))
      s"$prepend\n$reply"

    case BotMessage(_, _, message) if enableExclamation && message.startsWith("!") =>
      val (prepend, reply) = process(ltrim(message.drop(1)))
      s"$prepend\n$reply"
  }

  def process(message: String): (String, String) =
    supportedCommands find message.startsWith match {
      case Some(command) if command == ":help" || command == "?" => ("/quote", help)
      case Some(command) => ("/quote", repl.processCode(command))
      case None if message.startsWith(":") => ("/quote", unrecognizedCommand)
      case None => ("/code", repl.processCode(message))
    }
}
