package com.github.davidhoyt

import akka.actor.ActorRef

case class BotMessage(config: BotConfiguration, from: String, message: String)
case class BotConfiguration(myNickName: String, myMentionName: String, myRoomId: String, hipchat: ActorRef)

/**
 * Guaranteed to process messages sequentially and in a thread-safe manner.
 * Therefore mutable state is acceptable. It is effectively an actor.
 */
trait Bot {
  type MessageReceived = PartialFunction[BotMessage, String]
  def messageReceived: MessageReceived
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