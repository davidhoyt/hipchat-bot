package com.github.davidhoyt.hipchat

import javax.net.ssl.{SSLSession, HostnameVerifier}

import akka.actor._

class HipChatActor extends Actor with ActorLogging {
  import com.github.davidhoyt.Bot
  import com.github.davidhoyt.xmpp.Xmpp
  import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode
  import org.jivesoftware.smack._
  import org.jivesoftware.smack.packet.Presence
  import org.jivesoftware.smack.tcp.XMPPTCPConnection
  import org.jivesoftware.smackx.ping.PingManager
  import scala.collection.mutable
  import scala.concurrent.duration._
  import scala.util.control.NonFatal
  import HipChat._

  import context.dispatcher

  val xmpp = Xmpp(context.system)
  var hipChatConnection: Connect = _
  var roomsJoined = mutable.Map.empty[ActorRef, JoinRoom[_ <: Bot]]
  var configuration: HipChatConfiguration = _
  var connection: XMPPTCPConnection = _
  var connected = false
  var stopping = false
  var attempt = 0

  def presenceFor(update: Status, status: String = ""): Presence = {
    val presence = new Presence(Status.`type`(update), status, 0, Status.mode(update))
    presence.setLanguage("en_US")
    presence
  }

  def statusUpdate(update: Status, status: String = "") =
//    connection.sendStanza(presenceFor(update, status))
    connection.sendPacket(presenceFor(update, status))

  def handleReconnect(): Unit = if (!stopping) {
    if (attempt <= 0)
      self ! hipChatConnection
    else
      context.system.scheduler.scheduleOnce(math.pow(2, attempt - 1).seconds)(self ! hipChatConnection)
    attempt = math.min(attempt + 1, 5)
  }

  def logAsWarning: Receive = {
    case other =>
      log.warning("Received unexpected message {}", other)
  }

  override def receive: Receive = receiveConnect orElse receiveConnected orElse logAsWarning

  val receiveConnect: Receive = {
    case conn @ Connect(hipChatConfig @ HipChatConfiguration(serviceName, host, port, userName, password)) =>
      log.info("Attempting connection to {}:{}", conn.configuration.host, conn.configuration.port)
//      val config = XMPPTCPConnectionConfiguration.builder()
//        .setHost(host)
//        .setPort(port)
//        .setResource("bot")
//        .setServiceName(serviceName)
//        .setSendPresence(false)
//        .setSecurityMode(SecurityMode.required)
//        .setLegacySessionDisabled(false)
//        .setUsernameAndPassword(userName, password)
//        .setHostnameVerifier(new HostnameVerifier {
//          override def verify(s: String, sslSession: SSLSession): Boolean = true
//        })
//        .build()
      val config = new ConnectionConfiguration(host, port, "bot")
      config.setSendPresence(false)
      config.setReconnectionAllowed(false)
      config.setSecurityMode(SecurityMode.required)
      config.setHostnameVerifier(new HostnameVerifier {
        override def verify(s: String, sslSession: SSLSession): Boolean = true
      })

      configuration = hipChatConfig
      hipChatConnection = conn

      xmpp.connection.foreach(_.disconnect())

      connection = new XMPPTCPConnection(config)
      connection.addConnectionListener(new ConnectionListener {
        override def reconnectionFailed(e: Exception): Unit = onDisconnect()
        override def reconnectionSuccessful(): Unit = onConnect(connection)
        override def reconnectingIn(seconds: Int): Unit = ()
//        override def authenticated(connection: XMPPConnection, resumed: Boolean): Unit = ()
        override def authenticated(connection: XMPPConnection): Unit = ()

        override def connectionClosedOnError(e: Exception): Unit =
          onDisconnect()
        override def connectionClosed(): Unit =
          onDisconnect()
        override def connected(connection: XMPPConnection): Unit =
          onConnect(connection)

        def onConnect(connection: XMPPConnection): Unit = {
          xmpp.connection = Some(connection)
        }

        def onDisconnect(): Unit = {
          HipChatActor.this.connected = false
          xmpp.connection = None
          handleReconnect()
        }
      })

      try {
        connection.connect()
        connection.login(userName, password)
        //connection.sendPacket(presenceFor(Available))

        connected = true

        log.info(s"Hipchat connected successfully")

        for ((roomActor, _) <- roomsJoined)
          roomActor ! RejoinRoom

        self ! StatusUpdate(Available)

        val pingManager = PingManager.getInstanceFor(connection)
        pingManager.setPingInterval(5)

      } catch {
        case NonFatal(t) =>
          log.error(s"Unable to connect to HipChat")
          if (connection.isConnected)
            connection.disconnect(presenceFor(Away))
          else
            handleReconnect()
      }

    case RoomJoinedComplete(_, _) => //ignore
      //Will get this if there was a disconnect and then a reconnect.
  }

  val receiveConnected: Receive = {
    case join @ JoinRoom(roomId, _, _, _, _, recipient) =>
      val ref = context.actorOf(HipChatRoom.props, roomId)
      roomsJoined += (ref -> join.copy(recipient = None))
      ref ! join.copy(recipient = recipient.orElse(Some(sender())))

    case StatusUpdate(update, status) =>
//      xmpp.connection foreach (_.sendStanza(presenceFor(update, status)))
      xmpp.connection foreach (_.sendPacket(presenceFor(update, status)))
  }

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    attempt = 0
    stopping = false
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    stopping = true
    if ((connection ne null) && connection.isConnected)
      connection.disconnect(presenceFor(Away))
    xmpp.connection = None
    super.postStop()
  }
}
