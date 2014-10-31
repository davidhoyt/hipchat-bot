package com.github.davidhoyt

import java.security.{AccessController, PrivilegedAction}

import org.wildfly.security.manager.WildFlySecurityManager

object Security {
  def install(): Unit =
    //System.setSecurityManager(new SecurityManager)
    WildFlySecurityManager.install()

  def privileged[T](work: => T): T =
    //AccessController.doPrivileged(new PrivilegedAction[T] {
    //  override def run(): T = work
    //})
    WildFlySecurityManager.doUnchecked(new PrivilegedAction[T] {
      override def run(): T = work
    })

  def unprivileged[T](work: => T): T =
    WildFlySecurityManager.doChecked(new PrivilegedAction[T] {
      override def run(): T = work
    })
}
