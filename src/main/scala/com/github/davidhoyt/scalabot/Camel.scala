package com.github.davidhoyt.scalabot

//import org.apache.camel.component.xmpp.XmppMessage
//import org.apache.camel.{Processor, Predicate, Exchange}
//import org.apache.camel.builder.RouteBuilder
//import org.jivesoftware.smack.packet.Message

//trait Bot {
//  def process(message: String): Option[String]
//  def validate(message: String): Boolean = true //message.startsWith(mentionName)
//}

//object CamelBot {
//  implicit def fnToPredicate(fn: Exchange => Boolean): Predicate =
//    new Predicate {
//      override def matches(exchange: Exchange): Boolean =
//        fn(exchange)
//    }
//
//  def messageProcessor(bot: Bot): Processor =
//    new Processor {
//      override def process(exchange: Exchange): Unit = {
//        bot.process(exchangeToMessage(exchange).getBody) match {
//          case Some(message) =>
//            exchange.getOut.setBody(message, classOf[String])
//          case _ =>
//            exchange.setProperty(Exchange.ROUTE_STOP, java.lang.Boolean.TRUE)
//        }
//      }
//    }
//
//  implicit def exchangeToMessage(exchange: Exchange): Message =
//    exchange.getIn(classOf[XmppMessage]).getXmppMessage
//
//  def considerMessage(fn: String => Boolean): Exchange => Boolean =
//    (ex: Exchange) => (exchangeToMessage _ andThen (_.getBody) andThen fn)(ex)
//
////  def considerFrom(fn: String => Boolean): Exchange => Boolean =
////    (ex: Exchange) => (exchangeToMessage _ andThen extract andThen fn)(ex)
////
////  def extract(m: Message): String =
////    m.getFrom
////
////  def ignoreMyself(bot: Bot)(from: String): Boolean =
////    from != bot.user
//
//  def apply(chatEndpoint: String, bot: Bot): RouteBuilder =
//    new RouteBuilder() {
//      def configure() = {
//        from(chatEndpoint)
//          //.filter(considerFrom(ignoreMyself(bot)))
//          .filter(considerMessage(bot.validate))
//          .process(messageProcessor(bot))
//          .to(chatEndpoint)
//      }
//  }
//}
