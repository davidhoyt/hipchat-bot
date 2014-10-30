package com.github.davidhoyt.scalabot

import java.io.BufferedReader

import scala.reflect.internal.util.ScalaClassLoader._
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._

class CustomILoop(input: BufferedReader, output: JPrintWriter, override val prompt: String = "")
  extends ILoop(input, output) {

  override def printWelcome(): Unit = ()

  override def process(settings: Settings): Boolean = savingContextLoader {
    this.settings = settings
    createInterpreter()

    in = SimpleReader(input, output, interactive = true)
    intp.initializeSynchronous()
    val errors = !intp.reporter.hasErrors

    try loop()
    catch AbstractOrMissingHandler()
    finally closeInterpreter()

    errors
  }

  // return false if repl should exit
  override def processLine(line: String): Boolean = {
    val response = (line ne null) && (command(line) match {
      case Result(false, _) => false
      case Result(_, Some(line)) => true
      case _ => true
    })

    //Write a special value so we know when the output is complete for
    //this line.
    output.write('\0')
    response
  }

  /** Interpret expressions starting with the first line.
    * Read lines until a complete compilation unit is available
    * or until a syntax error has been seen.  If a full unit is
    * read, go ahead and interpret it.  Return the full string
    * to be recorded for replay, if any.
    */
  override def interpretStartingWith(code: String): Option[String] = {
    // signal completion non-completion input has been received
    in.completion.resetVerbosity()

    def reallyInterpret = {
      val reallyResult = intp.interpret(code)
      (reallyResult, reallyResult match {
        case IR.Error => None
        case IR.Success => Some(code)
        case IR.Incomplete =>
          in.readLine("" /* removed continue prompt */) match {
            case null =>
              // we know compilation is going to fail since we're at EOF and the
              // parser thinks the input is still incomplete, but since this is
              // a file being read non-interactively we want to fail.  So we send
              // it straight to the compiler for the nice error message.
              intp.compileString(code)
              None

            case line => interpretStartingWith(code + "\n" + line)
          }
      })
    }

    /** Here we place ourselves between the user and the interpreter and examine
      * the input they are ostensibly submitting.  We intervene in several cases:
      *
      * 1) If the line starts with "scala> " it is assumed to be an interpreter paste.
      * 2) If the line starts with "." (but not ".." or "./") it is treated as an invocation
      * on the previous result.
      * 3) If the Completion object's execute returns Some(_), we inject that value
      * and avoid the interpreter, as it's likely not valid scala code.
      */
    if (code == "") None
    else if (Completion.looksLikeInvocation(code) && intp.mostRecentVar != "") {
      interpretStartingWith(intp.mostRecentVar + code)
    }
    else if (code.trim startsWith "//") {
      // line comment, do nothing
      None
    }
    else
      reallyInterpret._2
  }
}
