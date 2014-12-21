package com.github.davidhoyt.scalabot

import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.duration._

class REPL(val name: String, val maxLines: Int = 8, val maxLength: Int = 1500, val timeout: FiniteDuration = 10.seconds, val blacklist: Seq[String] = Seq(), val runOnStartup: String = "")
  extends AnyRef
  with LazyLogging {

  import com.github.davidhoyt.Sandbox
  import java.io._
  import scala.tools.nsc.{Settings, NewLinePrintWriter}
  import scala.tools.nsc.interpreter._
  import scala.tools.nsc.util.ClassPath
  import Sandbox._

  private[this] val configuration = Configuration(
    name        = s"scala-interpreter-$name",
    timeout     = timeout,
    maxLines    = maxLines,
    maxLength   = maxLength,
    writer      = new StringWriter(),
    permissions = new File("scalabot-sandbox.policy")
  )

  import configuration.output

  private[this] val closingLock = new AnyRef
  private[this] var closing = false

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

  val interpreter = {
    val main = new IMain(settings, new NewLinePrintWriter(output, true))
    main.quietRun(runOnStartup)
    main.isettings.maxPrintString = maxLength
    main
  }

  def echo(message: String): Unit = {
    output println message
    output.flush()
  }

  def resetCommand(): Unit = {
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

  def typeCommand(expr: String): Unit = {
    val s = expr.trim
    interpreter.typeCommandInternal(s stripPrefix "-v " trim, verbose = s startsWith "-v ")
  }

  def kindCommand(expr: String): Unit = {
    val s = expr.trim
    interpreter.kindCommandInternal(s stripPrefix "-v " trim, verbose = s startsWith "-v ")
  }

  def processCode(code: String): String = {
    var filterOutput: Option[String => String] = None

    val (_, out) = Sandbox(configuration) {
      code match {
        case command if command == ":reset" => resetCommand()
        case command if command == ":warnings" => warningsCommand()
        case command if command.startsWith(":type") => typeCommand(command.drop(":type".length))
        case command if command.startsWith(":kind") => kindCommand(command.drop(":kind".length))
        case block =>
          import Results._
          interpreter.interpret(block) match {
            case Success => filterOutput = Some(out => out.replaceAll("(?m:^res[0-9]+: )", ""))
            case Error => filterOutput = Some(out => out.replaceAll("^<console>:[0-9]+: ", ""))
            case _ =>
          }
      }
    }

    filterOutput.getOrElse(identity[String]_)(out)
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
    if (closing)
      throw new IllegalStateException(s"Attempted to restart a closed REPL instance")
    true
  }

  def close(): Unit = closingLock synchronized {
    if (!closing)
      closing = true
  }

  override def finalize(): Unit = {
    close()
    super.finalize()
  }
}
