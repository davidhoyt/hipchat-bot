package com.github.davidhoyt

object Sandbox {
  import java.io.{File, PrintStream, StringWriter, PrintWriter => JPrintWriter}
  import java.net.URL
  import java.security.PermissionCollection
  import scala.concurrent.duration._
  import scala.language.implicitConversions

  case class Configuration(name: String, trimOutput: Boolean = true, maxLines: Int = 8, maxLength: Int = 1500, timeout: FiniteDuration = 10.seconds, writer: StringWriter = new StringWriter(), permissions: PermissionCollection = new ReadWritePermissionCollection) {
    require(maxLength > 3)
    require(maxLines > 0)

    val output = new JPrintWriter(writer)
    val outPrintStream = new PrintStream(new WriterOutputStream(writer), true)

    def clearOutput(): Unit = writer.synchronized {
      writer.getBuffer.setLength(0)
    }
  }

  implicit def fileToPermissions(policy: File): PermissionCollection =
    urlToPermissions(policy.toURI.toURL)

  implicit def urlToPermissions(policy: URL): PermissionCollection = {
    import java.security.CodeSource
    import java.security.cert.Certificate
    import sun.security.provider.PolicyFile

    val sandboxPolicy = new PolicyFile(policy)

    val sandboxPermissions = sandboxPolicy.getPermissions(new CodeSource(null, null.asInstanceOf[Array[Certificate]]))
    sandboxPermissions
  }

  private[this] def createDefaultPermissions: PermissionCollection = {
    import java.security.AllPermission

    val p = new ReadWritePermissionCollection
    p.add(SandboxSecurityManager.CHANGE_SANDBOX_PERMISSIONS)
    p.add(new AllPermission())
    p
  }

  def install(defaultPermissions: PermissionCollection = createDefaultPermissions): Unit =
    System.setSecurityManager(new SandboxSecurityManager(defaultPermissions))

  def changePermissionsForCurrentThread(permissions: PermissionCollection): Unit =
    Option(System.getSecurityManager).foreach(_.asInstanceOf[SandboxSecurityManager].changePermissions(permissions))

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
            //Thread.currentThread().setContextClassLoader(new SecureClassLoader() {})
            changePermissionsForCurrentThread(permissions)

            workResult = Some(work)

          } catch {
//            case sec: SecurityError =>
//
//              sec.printStackTrace(output)
//              //Option(sec.getCause).foreach(_.printStackTrace(output))
//              output.println(sec.getMessage + "\n") // s"[ERROR] You do not have sufficient privileges to execute the provided code.\n")
            case t: Throwable =>
              t.printStackTrace()
              t.printStackTrace(output)
              output.println("---")
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
    if (forciblyStopped || out.contains("java.lang.ThreadDeath")) {
      sb.clear()
      sb.append(s"[ERROR] The provided code is taking too long and was forcibly stopped.\n")
    }

    if (out.contains("com.github.davidhoyt.SecurityError")) {
      //sb.clear()
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
