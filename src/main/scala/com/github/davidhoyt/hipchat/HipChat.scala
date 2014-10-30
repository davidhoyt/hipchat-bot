package com.github.davidhoyt.hipchat

import com.github.davidhoyt.{BotFactory, Bot}

object HipChat {
  case class HipChatConfiguration(host: String, port: Int, userName: String, password: String)
  case class Connect(configuration: HipChatConfiguration)
  case class JoinRoom[T <: Bot](roomId: String, nickName: String, mentionName: String, botFactory: BotFactory[T], args: Seq[Any] = Seq())
  case class StatusUpdate(update: Status, status: String = "")
  case class MessageReceived(from: String, message: String)
  case object KeepAlive

  sealed trait Status
  case object Available extends Status
  case object Away extends Status
  case object DoNotDisturb extends Status


  import akka.actor.SupervisorStrategy.{Resume, Decider, Stop}
  import akka.actor.{DeathPactException, ActorKilledException, ActorInitializationException, Props}
  import org.jivesoftware.smack.packet.Presence

  object Status {
    def mode(status: Status): Presence.Mode =
      status match {
        case Available => Presence.Mode.available
        case Away => Presence.Mode.away
        case DoNotDisturb => Presence.Mode.dnd
        case _ => Presence.Mode.available
      }

    def `type`(status: Status): Presence.Type =
      status match {
        case Available => Presence.Type.available
        case _ => Presence.Type.unavailable
      }
  }

  val resumeDecider: Decider = {
    case _: ActorInitializationException ⇒ Stop
    case _: ActorKilledException         ⇒ Stop
    case _: DeathPactException           ⇒ Stop
    case _: Exception                    ⇒ Resume
  }

  def props: Props =
    Props[HipChatActor]
}
