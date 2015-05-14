package com.github.davidhoyt

package object xmpp {
  import org.jivesoftware.smack.PacketListener
  import org.jivesoftware.smack.packet.{Message, Packet}
  import org.jivesoftware.smackx.muc.MultiUserChat
  import rx.lang.scala.{Subscriber, Observable}

  implicit class MultiUserChatEnhancements(val muc: MultiUserChat) extends AnyVal {
    def toObservable: Observable[Message] =
      Observable { subscriber: Subscriber[Message] =>
//        val messageListener = new MessageListener {
//          override def processMessage(message: Message): Unit =
//            subscriber.onNext(message)
//        }
//
//        muc.addMessageListener(messageListener)
//        subscriber.add {
//          muc.removeMessageListener(messageListener)
//          ()
//        }

        val packetListener =
          new PacketListener {
            override def processPacket(packet: Packet): Unit =
              packet match {
                case msg: Message =>
                  subscriber.onNext(msg)
              }
          }

        muc.addMessageListener(packetListener)
        subscriber.add(muc.removeMessageListener(packetListener))
      }
  }
}
