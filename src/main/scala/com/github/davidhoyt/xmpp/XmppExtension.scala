package com.github.davidhoyt.xmpp

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import org.jivesoftware.smack.XMPPConnection

trait Xmpp {
  def actorSystem: ActorSystem

  var connection: Option[XMPPConnection] = None
}

object Xmpp extends ExtensionId[XmppExtension] with ExtensionIdProvider {
  override def lookup =
    Xmpp

  override def createExtension(system: ExtendedActorSystem) =
    new XmppExtension(system)
}

class XmppExtension(val actorSystem: ActorSystem)
  extends Extension with Xmpp
