package com.github.davidhoyt

import java.security.{AccessController, PrivilegedAction}

object Sandbox {
  import java.io.{PrintStream, Writer, StringWriter, PrintWriter => JPrintWriter}
  import scala.concurrent.duration._

  case class Configuration(name: String, trimOutput: Boolean = true, maxLines: Int = 8, maxLength: Int = 1500, timeout: FiniteDuration = 10.seconds, writer: StringWriter = new StringWriter()) {
    require(maxLength > 3)
    require(maxLines > 0)

    val output = new JPrintWriter(writer)
    val outPrintStream = new PrintStream(new WriterOutputStream(writer), true)

    def clearOutput(): Unit = writer.synchronized {
      writer.getBuffer.setLength(0)
    }
  }

  def install(): Unit = {
    import java.io.FilePermission
    import java.lang.reflect.ReflectPermission
    import java.net.NetPermission
    import java.util.PropertyPermission

//    import sun.security.provider.PolicyFile
//    import sun.security.provider.PolicyParser

    val perms = new MyPermissionCollection()
    //perms.add(new PropertyPermission("*", "read"))
    perms.add(new PropertyPermission("line.separator", "read"))
    perms.add(new PropertyPermission("java.specification.version", "read"))
    perms.add(new PropertyPermission("scala.*", "read"))
    perms.add(new PropertyPermission("scalac.*", "read"))
    perms.add(new FilePermission("<<ALL FILES>>", "read"))
    perms.add(new NetPermission("specifyStreamHandler"))
    perms.add(new RuntimePermission("getClassLoader"))
    perms.add(new RuntimePermission("setContextClassLoader"))
    perms.add(new ReflectPermission("suppressAccessChecks"))
    perms.add(new RuntimePermission("accessDeclaredMembers"))
    perms.add(new RuntimePermission("createClassLoader"))

    //perms.add(new RuntimePermission("createThread"))

    System.setSecurityManager(new SandboxSecurityManager(false, perms))
  }

  def enableForThisThread(): Unit =
    System.getSecurityManager.asInstanceOf[SandboxSecurityManager].enable()

  def disableForThisThread(): Unit =
    System.getSecurityManager.asInstanceOf[SandboxSecurityManager].disable()

  def privileged[T](work: => T): T =
    AccessController.doPrivileged(new PrivilegedAction[T] {
      override def run(): T = work
    })

  def unprivileged[T](work: => T): T =
    work

  def apply[T](configuration: Configuration)(work: => T): (Option[T], String) = {
    import configuration._

    var forciblyStopped = false
    var workResult: Option[T] = None

    try {
      clearOutput()

      val t = new Thread {
        override def run(): Unit = {
          try {
            ThreadPrintStream.setThreadLocalSystemOut(outPrintStream)
            enableForThisThread()

            workResult = Some(work)

          } catch {
            case sec: SecurityError =>
              sec.getCause.printStackTrace(output)
              output.println(s"[ERROR] You do not have sufficient privileges to execute the provided code.\n")
            case t: Throwable =>
              //t.printStackTrace(output)
              output.println(s"[ERROR] Exception during evaluation or provided code is taking too long and was forcibly stopped.\n")
          } finally {
            //Security.disableForThisThread()
          }
        }
      }

      t.setPriority(Thread.MIN_PRIORITY)
      t.setDaemon(true)
      t.setName(name)
      t.start()
      t.join(timeout.toMillis)
      if (t.isAlive) {
        forciblyStopped = true
        t.stop()
      }

    } catch {
      case t: Throwable =>
        //t.printStackTrace(output)
    } finally {
    }

    val out = writer.toString
    val sb = new StringBuilder(out) //filterOutput.getOrElse(identity[String]_)(out))

    //Ugly hack...
    if (forciblyStopped || out.startsWith("java.lang.ThreadDeath")) {
      sb.clear()
      sb.append(s"[ERROR] The provided code is taking too long and was forcibly stopped.\n")
    }

    if (out.startsWith("com.github.davidhoyt.SecurityError")) {
      sb.clear()
      sb.append(s"[ERROR] You do not have sufficient privileges to execute the provided code.\n")
    }

    val workOutput =
      if (trimOutput) {
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
      } else {
        sb.lines.mkString("\n")
      }

    sb.clear()
    clearOutput()

    (workResult, workOutput)
  }
}
