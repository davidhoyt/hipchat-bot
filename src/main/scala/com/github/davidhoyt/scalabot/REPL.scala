package com.github.davidhoyt.scalabot

import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.duration._
import scala.reflect._
import scala.tools.nsc.interpreter.LoopCommands
import scala.tools.nsc.interpreter.StdReplTags._

class REPL(val name: String, val maxLines: Int = 8, val maxLength: Int = 1500, val timeout: FiniteDuration = 10.seconds, val blacklist: Seq[String] = Seq())
  extends AnyRef
  with LoopCommands
  with LazyLogging {

  import com.Ostermiller.util.CircularCharBuffer
  import com.github.davidhoyt.{WriterOutputStream, ThreadPrintStream, Security}
  import java.io._
  import scala.tools.nsc.Settings
  import scala.tools.nsc.interpreter._
  import scala.tools.nsc.util.ClassPath

  private[this] val outBuffer = new CircularCharBuffer(maxLength)
  private[this] val outReader = outBuffer.getReader
  private[this] val outWriter = outBuffer.getWriter
  private[this] val outPrintStream = new PrintStream(new WriterOutputStream(outWriter), true)
  private[this] val output = new JPrintWriter(outWriter, true)

  private[this] val closingLock = new AnyRef
  private[this] var closing = false

  val out = output

  require(maxLength > 3)
  require(maxLines > 0)

  private[this] val classpath = {
    //It's important this is done outside of settings() so that
    //the SecurityManager is not potentially in place.
    val javaHome = sys.props("java.home")
    val filtered =
      ClassPath.split(sys.props("java.class.path"))
        .filterNot { entry =>
          blacklist.exists(w => entry.endsWith(w))
        }
    ClassPath.join(filtered:_*)
  }

  import scala.tools.nsc.interpreter._
  val interpreter = new IMain(settings, output)

  def echo(message: String): Unit = {
    output println message
    output.flush()
  }

  def resetCommand() {
    echo("Resetting interpreter state.")
    if (interpreter.namedDefinedTerms.nonEmpty)
      echo("Forgetting all expression results and named terms: " + interpreter.namedDefinedTerms.mkString(", "))
    if (interpreter.definedTypes.nonEmpty)
      echo("Forgetting defined types: " + interpreter.definedTypes.mkString(", "))

    interpreter.reset()
    //Set the phase to "typer"
    //interpreter beSilentDuring interpreter.setExecutionWrapper(pathToPhaseWrapper)
  }

  def warningsCommand(): Result = {
    if (interpreter.lastWarnings.isEmpty)
      "Can't find any cached warnings."
    else
      interpreter.lastWarnings foreach {
        case (pos, msg) =>
          interpreter.reporter.warning(pos, msg)
      }
  }

  def typeCommand(line0: String): Result = {
    line0.trim match {
      case "" => ":type [-v] <expression>"
      case s  => interpreter.typeCommandInternal(s stripPrefix "-v " trim, verbose = s startsWith "-v ")
    }
  }

  def kindCommand(expr: String): Result = {
    expr.trim match {
      case "" => ":kind [-v] <expression>"
      case s  => interpreter.kindCommandInternal(s stripPrefix "-v " trim, verbose = s startsWith "-v ")
    }
  }

  def processCode(code: String): String = {
    try {
      outBuffer.clear()

      val t = new Thread {
        override def run(): Unit = {
          try {
            ThreadPrintStream.setThreadLocalSystemOut(outPrintStream)

            code match {
              case command if command == ":reset" => resetCommand()
              case command if command == ":warnings" => warningsCommand()
              case command if command.startsWith(":type") => typeCommand(command.drop(":type".length))
              case command if command.startsWith(":kind") => kindCommand(command.drop(":kind".length))
              case block => Security.unprivileged(interpreter.interpret(block))
            }

          } catch {
            case _: Throwable =>
              output.println(s"[ERROR] Exception during evaluation or provided code is taking too long and was forcibly stopped.")
              false
          } finally {
            output.write('\0')
          }
        }
      }

      t.setPriority(Thread.MIN_PRIORITY)
      t.setDaemon(true)
      t.setName(s"scala-interpreter-$name")
      t.start()
      t.join(timeout.toMillis)
      if (t.isAlive)
        t.stop()

    } catch {
      case t: Throwable =>
        t.printStackTrace(output)
        false
    } finally {
    }

    val charBuffer = new Array[Char](256)
    var read = 0
    val sb = new StringBuilder()
    var done = false
    do {
      read = outReader.read(charBuffer)
      if (read > 0 && charBuffer(read - 1) == '\0') {
        read = read - 1
        done = true
      }
      sb.appendAll(charBuffer, 0, read)
    } while (read > 0 && !done)

    //Ugly hack...
    if (sb.startsWith("java.lang.ThreadDeath")) {
      sb.clear()
      sb.append(s"[ERROR] The provided code is taking too long and was forcibly stopped.\n")
    }

    val (reduced, needsEllipsis) =
      if (sb.size < maxLength)
        (sb, false)
      else
        (sb.take(maxLength - "...".length), true)

    val lines = reduced.lines.take(maxLines).toSeq

    val result = lines.mkString("\n")

    if (needsEllipsis || lines.length == maxLines)
      result + "..."
    else
      result
  }

  def settings = {
    val settings = new Settings()
    settings.Xnojline.value = true
    if (settings.classpath.isDefault)
      settings.classpath.value = classpath
    settings
  }

  def start(): Boolean = closingLock synchronized {
    if (!closing) {
      outBuffer.clear()
    } else {
      throw new IllegalStateException(s"Attempted to restart a closed REPL instance")
    }
    true
  }

  def close(): Unit = closingLock synchronized {
    if (!closing) {
      closing = true
      outBuffer.clear()
    }
  }

  override def finalize(): Unit = {
    close()
    super.finalize()
  }
}
