package com.github.davidhoyt

import akka.actor.ActorRef
import com.typesafe.scalalogging.Logging

case class BotInitialize(nickName: String, mentionName: String, roomId: String, hipchat: Option[ActorRef], room: Option[ActorRef])
case class BotMessageReceived(from: String, message: String)
case class BotMessage(message: String)

/**
 * Guaranteed to process messages sequentially and in a thread-safe manner.
 * Therefore mutable state is acceptable. It is effectively an actor.
 */
trait Bot { self: Logging =>
  type InitializeReceived = PartialFunction[BotInitialize, Unit]
  type MessageReceived = PartialFunction[BotMessageReceived, Seq[BotMessage]]

  def messageReceived: MessageReceived

  def initializeReceived: InitializeReceived = {
    case initialize =>
      logger.info(s"Initialized bot with $initialize")
  }
}

/**
 * A factory for producing instances of [[Bot]] given some arguments.
 *
 * @tparam T A [[Bot]] type
 */
trait BotFactory[T <: Bot] {
  def apply(args: Seq[Any]): Option[T]
}

object BotFactory {
  import scala.reflect.runtime.universe._

  /**
   * Creates [[BotFactory]] instances for a given type as long as that type
   * is a [[Bot]] and the bot is declared as a class. A concrete type is
   * required in order to instantiate it from its primary constructor.
   */
  implicit def apply[T <: Bot](implicit tag: TypeTag[T]): BotFactory[T] = {
    val typeSymbol = tag.tpe.typeSymbol
    val classSymbol =
      if (!typeSymbol.isModule)
        typeSymbol.asClass
      else
        typeSymbol.companion.asClass

    require(classSymbol.isClass, s"Concrete class is required. Given: ${tag.toString()}")

    val mirror = runtimeMirror(this.getClass.getClassLoader)
    val classMirror = mirror.reflectClass(classSymbol)

    val primaryConstructor = tag.tpe.decl(termNames.CONSTRUCTOR).asTerm.alternatives.collectFirst {
      case ctor: MethodSymbol if ctor.isPrimaryConstructor =>
        classMirror.reflectConstructor(ctor)
    }

    new BotFactory[T] {
      override def apply(args: Seq[Any]): Option[T] =
        primaryConstructor map (_(args:_*).asInstanceOf[T])
    }
  }
}