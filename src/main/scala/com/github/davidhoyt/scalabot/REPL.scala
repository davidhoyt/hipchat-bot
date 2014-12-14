package com.github.davidhoyt.scalabot

import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.duration._

class REPL(val name: String, val maxLines: Int = 8, val maxLength: Int = 1500, val timeout: FiniteDuration = 10.seconds, val blacklist: Seq[String] = Seq(), val runOnStartup: String = "")
  extends AnyRef
  with LazyLogging {

  import com.github.davidhoyt.{WriterOutputStream, ThreadPrintStream, Security}
  import java.io._
  import scala.tools.nsc.{Settings, NewLinePrintWriter}
  import scala.tools.nsc.interpreter._
  import scala.tools.nsc.util.ClassPath

  private[this] val writer = new StringWriter()
  private[this] val output = new JPrintWriter(writer)
  private[this] val outPrintStream = new PrintStream(new WriterOutputStream(writer), true)

  private[this] val closingLock = new AnyRef
  private[this] var closing = false

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

  val interpreter = {
    val main = new IMain(settings, new NewLinePrintWriter(output, true))
    main.quietRun(runOnStartup)
    main
  }

  def clearOutput(): Unit =
    writer.getBuffer.setLength(0)

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

    //Ensure we re-run startup code after clearing everything out.
    interpreter.quietRun(runOnStartup)

    //Set the phase to "typer"
    //interpreter beSilentDuring interpreter.setExecutionWrapper(pathToPhaseWrapper)
  }

  def warningsCommand(): Unit = {
    if (interpreter.lastWarnings.isEmpty)
      output.println("Can't find any cached warnings.")
    else
      interpreter.lastWarnings foreach {
        case (pos, msg) =>
          interpreter.reporter.warning(pos, msg)
      }
  }

  def typeCommand(line0: String): Unit = {
    val s = line0.trim
    interpreter.typeCommandInternal(s stripPrefix "-v " trim, verbose = s startsWith "-v ")
  }

  def kindCommand(expr: String): Unit = {
    val s = expr.trim
    interpreter.kindCommandInternal(s stripPrefix "-v " trim, verbose = s startsWith "-v ")
  }

  def processCode(code: String): String = {
    var filterOutput: Option[String => String] = None
    var forciblyStopped = false

    try {
      clearOutput()

      val t = new Thread {
        override def run(): Unit = {
          try {
            ThreadPrintStream.setThreadLocalSystemOut(outPrintStream)

            code match {
              case command if command == ":reset" => resetCommand()
              case command if command == ":warnings" => warningsCommand()
              case command if command.startsWith(":type") => typeCommand(command.drop(":type".length))
              case command if command.startsWith(":kind") => kindCommand(command.drop(":kind".length))
              case block =>
                import Results._
                Security.unprivileged(interpreter.interpret(block)) match {
                  case Success => filterOutput = Some(out => out.replaceAll("(?m:^res[0-9]+: )", ""))
                  case Error => filterOutput = Some(out => out.replaceAll("^<console>:[0-9]+: ", ""))
                  case _ =>
                }
            }

          } catch {
            case t: Throwable =>
              //t.printStackTrace(output)
              output.println(s"[ERROR] Exception during evaluation or provided code is taking too long and was forcibly stopped.")
          } finally {
          }
        }
      }

      t.setPriority(Thread.MIN_PRIORITY)
      t.setDaemon(true)
      t.setName(s"scala-interpreter-$name")
      t.start()
      t.join(timeout.toMillis)
      if (t.isAlive) {
        forciblyStopped = true
        t.stop()
      }

    } catch {
      case t: Throwable =>
        t.printStackTrace(output)
    } finally {
    }

    val out = writer.toString
    val sb = new StringBuilder(filterOutput.getOrElse(identity[String]_)(out))

    //Ugly hack...
    if (forciblyStopped || out.startsWith("java.lang.ThreadDeath")) {
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

    sb.clear()
    clearOutput()

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

    settings.deprecation.value = true
    settings.feature.value = false

    settings
  }

  def start(): Boolean = closingLock synchronized {
    if (!closing) {
      clearOutput()
    } else {
      throw new IllegalStateException(s"Attempted to restart a closed REPL instance")
    }
    true
  }

  def close(): Unit = closingLock synchronized {
    if (!closing) {
      closing = true
      clearOutput()
    }
  }

  override def finalize(): Unit = {
    close()
    super.finalize()
  }
}
