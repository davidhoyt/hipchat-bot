package com.github.davidhoyt.hipchat

import akka.actor._
import com.github.davidhoyt.{BotFactory, BotConfiguration, BotMessage, Bot}

object HipChatRoom {
  def props: Props =
    Props[HipChatRoomActor]
}

class HipChatRoomActor extends Actor with ActorLogging {
  import com.github.davidhoyt.xmpp._
  import org.jivesoftware.smack.SmackConfiguration
  import org.jivesoftware.smack.packet.Message
  import org.jivesoftware.smackx.muc.{DiscussionHistory, MultiUserChat}
  import rx.lang.scala.Observable
  import scala.util.control.NonFatal
  import HipChat._

  val xmpp = Xmpp(context.system)
  var muc: MultiUserChat = _
  var hipchat: ActorRef = _
  var obs: Observable[Message] = _
  var botConfiguration: BotConfiguration = _
  var botFactory: BotFactory[_ <: Bot] = _
  var bot: Option[Bot] = None

  override def receive: Receive = {
    case JoinRoom(roomId, joinNickName, joinMentionName, joinBotFactory, joinBotArgs) =>
      log.info("Joining room {} with {}/@{}", roomId, joinNickName, joinMentionName)

      val fullRoomId = s"$roomId@conf.hipchat.com"

      val history = new DiscussionHistory
      history.setMaxChars(0)
      history.setMaxStanzas(0)
      history.setSeconds(0)
      history.setSince(new java.util.Date())

      xmpp.connection map { connection =>
        try {
          muc = new MultiUserChat(connection, fullRoomId)
          muc.join(joinNickName, null, history, SmackConfiguration.getDefaultPacketReplyTimeout)

          hipchat = sender()
          botFactory = joinBotFactory
          botConfiguration = BotConfiguration(joinNickName, joinMentionName, roomId, hipchat)
          bot = botFactory(joinBotArgs)

          log.info("Successfully joined {}", roomId)

          context.become(joinedRoom)

          val myself = self
          muc.toObservable foreach { msg =>
            myself ! MessageReceived(msg.getFrom, Option(msg.getBody).getOrElse(""))
          }
        } catch {
          case NonFatal(t) =>
            log.error(t, s"Unable to join room $roomId")
        }
      }
    case other =>
      log.warning("Unexpectedly received {}", other)
  }

  def joinedRoom: Receive = {
    case msg @ MessageReceived(from, message) =>
      log.info("Received {}", msg)
      val idx = from.indexOf("/")
      val participantNickname =
        if (idx > 0)
          from.substring(idx + 1)
        else
          from

      if (participantNickname != botConfiguration.myNickName) {
        bot map { instance =>
          val botMsg = BotMessage(botConfiguration, participantNickname, message)
          if (instance.messageReceived.isDefinedAt(botMsg)) {
            try {
              muc.sendMessage(instance.messageReceived(botMsg))
            } catch {
              case NonFatal(t) =>
                log.error(t, "Error while processing message for bot: {}", botMsg)
            }
          }
        }
      }
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 1)(resumeDecider)
}
