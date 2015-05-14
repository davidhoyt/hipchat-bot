package com.github.davidhoyt.hipchat

import akka.actor._
import com.github.davidhoyt._

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
  import scala.util._
  import HipChat._

  import context.dispatcher

  val xmpp = Xmpp(context.system)
  var hipchatJoin: JoinRoom[_ <: Bot] = _
  var joined = false
  var muc: MultiUserChat = _
  var hipchat: ActorRef = _
  var nickName: String = _
  var obs: Observable[Message] = _
  var botFactory: BotFactory[_ <: Bot] = _
  var bot: Option[Bot] = None

  def xmppJoinRoom[T](roomId: String, nickName: String, password: String = null): Boolean = {
    val fullRoomId = roomId // s"$roomId@conf.hipchat.com"

    val myself = self

    val history = new DiscussionHistory
    history.setMaxChars(0)
    history.setMaxStanzas(0)
    history.setSeconds(0)
    history.setSince(new java.util.Date())

    try {
      val connection = xmpp.connection.getOrElse(throw new IllegalStateException(s"Unable to get XMPP connection"))

      Option(muc).foreach(m => Try(m.leave()))

//      muc = MultiUserChatManager.getInstanceFor(connection).getMultiUserChat(fullRoomId)
      muc = new MultiUserChat(connection, fullRoomId)
      muc.join(nickName, password, history, SmackConfiguration.getDefaultPacketReplyTimeout)

      log.info("Successfully joined {}", roomId)

      joined = true

      //Maintain a reference to the multi user chat observable.
      obs = muc.toObservable
      obs foreach { msg =>
        myself ! MessageReceived(msg.getFrom, Option(msg.getBody).getOrElse(""))
      }

      true
    } catch {
      case NonFatal(t) =>
        log.error(t, s"Unable to join room $roomId")
        joined = false
        false
    }
  }

  def rejoinRoom(): Unit =
    if (hipchatJoin ne null) {
      if (joined)
        xmppJoinRoom(hipchatJoin.roomId, hipchatJoin.nickName)
      else
        self ! hipchatJoin
    }

  override def receive: Receive = {
    case join @ JoinRoom(roomId, joinNickName, joinMentionName, joinBotFactory, joinBotArgs, joinRecipient) =>
      log.info("Joining room {} with {}/@{}", roomId, joinNickName, joinMentionName)

      val myself = self
      val originalSender = sender()

      hipchatJoin = join

      if (xmppJoinRoom(roomId, joinNickName)) {
        hipchat = originalSender
        botFactory = joinBotFactory
        bot = botFactory(joinBotArgs)
        nickName = joinNickName

        bot foreach { instance =>
          val initialize = BotInitialize(joinNickName, joinMentionName, roomId, Some(originalSender), Some(myself))
          if (instance.initializeReceived.isDefinedAt(initialize))
            instance.initializeReceived(initialize)
        }

        context.become(joinedRoom)

        joinRecipient foreach (_ ! RoomJoinedComplete(roomId, success = true))
      } else {
        joinRecipient foreach (_ ! RoomJoinedComplete(roomId, success = false))
      }

    case RejoinRoom =>
      rejoinRoom()

    case other =>
      log.warning("Unexpectedly received {}", other)
  }

  val joinedRoom: Receive = {
    case RejoinRoom =>
      rejoinRoom()

    case msg @ MessageReceived(from, message) =>
      log.info("Received {}", msg)
      val idx = from.indexOf("/")
      val participantNickName =
        if (idx > 0)
          from.substring(idx + 1)
        else
          from

      if (participantNickName != nickName || message.startsWith("!")) {
        bot foreach { instance =>
          val botMsg = BotMessageReceived(participantNickName, message)
          if (instance.messageReceived.isDefinedAt(botMsg)) {
            import scala.concurrent.duration._
            try {
              //Space message delivery out.
              for ((msg, idx) <- instance.messageReceived(botMsg).zipWithIndex)
                context.system.scheduler.scheduleOnce(idx.milliseconds * 10, self, msg)
            } catch {
              case NonFatal(t) =>
                log.error(t, "Error while processing message for bot: {}", botMsg)
            }
          }
        }
      }

    case BotMessage(message) =>
      muc.sendMessage(message)
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 1)(resumeDecider)
}
