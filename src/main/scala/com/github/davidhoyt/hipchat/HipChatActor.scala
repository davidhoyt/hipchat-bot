package com.github.davidhoyt.hipchat

import akka.actor._

class HipChatActor extends Actor with ActorLogging {
  import com.github.davidhoyt.xmpp.Xmpp
  import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode
  import org.jivesoftware.smack._
  import org.jivesoftware.smack.packet.Presence
  import org.jivesoftware.smack.tcp.XMPPTCPConnection
  import org.jivesoftware.smackx.ping.PingManager
  import scala.util.control.NonFatal
  import HipChat._

  val xmpp = Xmpp(context.system)
  var connection: XMPPTCPConnection = _

  def presenceFor(update: Status, status: String = ""): Presence = {
    val presence = new Presence(Status.`type`(update), status, 0, Status.mode(update))
    presence.setLanguage("en_US")
    presence
  }

  def statusUpdate(update: Status, status: String = "") =
    connection.sendPacket(presenceFor(update, status))

  override def receive: Receive = {
    case Connect(HipChatConfiguration(host, port, userName, password)) =>
      val config = new ConnectionConfiguration(host, port, "bot")
      config.setSendPresence(false)
      config.setReconnectionAllowed(true)
      config.setSecurityMode(SecurityMode.required)

      val connection = new XMPPTCPConnection(config)
      connection.addConnectionListener(new ConnectionListener {
        override def reconnectionFailed(e: Exception): Unit = ()
        override def reconnectionSuccessful(): Unit = ()
        override def reconnectingIn(seconds: Int): Unit = ()
        override def authenticated(connection: XMPPConnection): Unit = ()

        override def connectionClosedOnError(e: Exception): Unit =
          onDisconnect()
        override def connectionClosed(): Unit =
          onDisconnect()
        override def connected(connection: XMPPConnection): Unit =
          onConnect(connection)

        def onConnect(connection: XMPPConnection): Unit =
          xmpp.connection = Some(connection)

        def onDisconnect(): Unit = {
          xmpp.connection = None
        }
      })

      try {
        connection.connect()
        connection.login(userName, password)
        //connection.sendPacket(presenceFor(Available))

        log.info(s"Hipchat connected successfully")

        context.become(connected)

        self ! StatusUpdate(Available)

        val pingManager = PingManager.getInstanceFor(connection)
        pingManager.setPingInterval(5)

      } catch {
        case NonFatal(t) =>
          log.error(t, s"Unable to connect to HipChat")
          connection.disconnect(presenceFor(Away))
      }

    case other =>
      log.warning("Received unexpected message {}", other)
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    if ((connection ne null) && connection.isConnected)
      connection.disconnect(presenceFor(Away))
    xmpp.connection = None
    super.postStop()
  }

  def connected: Receive = {
    case join @ JoinRoom(roomId, _, _, _, _) =>
      context.actorOf(HipChatRoom.props, roomId) forward join

    case StatusUpdate(status) =>
      xmpp.connection map (_.sendPacket(presenceFor(status)))
  }
}
