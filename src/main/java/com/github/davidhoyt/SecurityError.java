package com.github.davidhoyt;

import java.security.AccessControlException;

public class SecurityError extends VirtualMachineError {
  public SecurityError(String message, AccessControlException accessControlException) {
    super(message, accessControlException);
  }
}
