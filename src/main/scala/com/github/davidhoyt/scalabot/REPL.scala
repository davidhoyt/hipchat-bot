package com.github.davidhoyt.scalabot

import java.io._

import com.Ostermiller.util.CircularCharBuffer
import com.github.davidhoyt.Security
import com.typesafe.scalalogging.slf4j.LazyLogging

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._

class REPL(val name: String, val replPrompt: String = "") extends LazyLogging {
  private[this] val inBuffer = new CircularCharBuffer(1024 * 1024)
  private[this] val inReader = inBuffer.getReader
  private[this] val inWriter = new BufferedWriter(inBuffer.getWriter)
  private[this] val outBuffer = new CircularCharBuffer(CircularCharBuffer.INFINITE_SIZE)
  private[this] val outReader = outBuffer.getReader
  private[this] val outWriter = outBuffer.getWriter
  private[this] val input = new BufferedReader(inReader)
  private[this] val output = new JPrintWriter(outWriter, true)

  private[this] val replLock = new AnyRef
  private[this] var replInstance: ILoop = _

  private[this] val closingLock = new AnyRef
  private[this] var closing = false
  
  def processCode(code: String): String = {
    outBuffer.clear()
    inBuffer.clear()

    inWriter.write(code)
    inWriter.newLine()
    inWriter.flush()

    val charBuffer = new Array[Char](256)
    var read = 0
    val sb = new StringBuilder()
    var done = false
    do {
      read = outReader.read(charBuffer)
      if (read > replPrompt.size && charBuffer(read - 1 - replPrompt.size) == '\0') {
        read = read - 1 - replPrompt.size
        done = true
      }
      sb.appendAll(charBuffer, 0, read)
    } while(read > 0 && !done)
    sb.toString()
  }

  private[this] def repl = replLock synchronized {
    if (replInstance eq null)
      newRepl
    else
      replInstance
  }

  private[this] def newRepl = replLock synchronized {
    replInstance = new CustomILoop(input, output, replPrompt)
    replInstance
  }

  def settings = {
    val settings = new Settings()
    settings.Xnojline.value = true
    if (settings.classpath.isDefault)
      settings.classpath.value = sys.props("java.class.path")
    settings
  }

  private[this] val thread = {
    val t = new Thread {
      override def run(): Unit =
        try {
          var attempts = 0
          while(!closing && attempts < 5) {
            if (!Security.unprivileged(repl process settings)) {
              attempts += 1
              logger.error(s"Unable to initialize Scala interpreter (attempt #$attempts)")
            } else {
              attempts = 0
            }
          }
        } catch {
          case _: Throwable => ()
        } finally {
          inWriter.close()
          outReader.close()
          println("Exiting")
        }
    }
    t.setDaemon(true)
    t.setName(s"scala-repl-$name")
    t
  }

  def start(): Boolean = closingLock synchronized {
    if (!closing) {
      inBuffer.clear()
      outBuffer.clear()
      thread.start()
    } else {
      throw new IllegalStateException(s"Attempted to restart a closed REPL instance")
    }
    true
  }

  def close(): Unit = closingLock synchronized {
    if (!closing) {
      closing = true
      inWriter.write(":quit")
      inWriter.newLine()
      inWriter.flush()
      thread.interrupt()
      thread.join()
    }
  }

  override def finalize(): Unit = {
    close()
    super.finalize()
  }
}
