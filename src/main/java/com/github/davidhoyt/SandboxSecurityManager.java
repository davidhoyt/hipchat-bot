package com.github.davidhoyt;

import java.security.*;
import java.security.cert.Certificate;

//Courtesy https://github.com/alphaloop/selective-security-manager

public class SandboxSecurityManager extends SecurityManager {
  public static final RuntimePermission CHANGE_SANDBOX_PERMISSIONS = new RuntimePermission("changeSandboxPermissions");
  public static final RuntimePermission CREATE_THREAD_PERMISSION = new RuntimePermission("createThread");

  InheritableThreadLocal<AccessControlContext> context = null;

  private static AccessControlContext accessControlContextFor(final PermissionCollection permissions) {
    final ProtectionDomain domain = new ProtectionDomain(new CodeSource(null, (Certificate[]) null), permissions);
    final AccessControlContext contextWithPermissions = new AccessControlContext(new ProtectionDomain[]{domain});
    return contextWithPermissions;
  }

  public SandboxSecurityManager(final PermissionCollection defaultPermissions) {
    final AccessControlContext defaultContext = accessControlContextFor(defaultPermissions);

    context = new InheritableThreadLocal<AccessControlContext>() {
      @Override
      protected AccessControlContext initialValue() {
        return defaultContext;
      }

      @Override
      protected AccessControlContext childValue(AccessControlContext parentContext) {
        checkPermission(CREATE_THREAD_PERMISSION);
        return parentContext;
      }

      @Override
      public void set(AccessControlContext newContext) {
        checkPermission(CHANGE_SANDBOX_PERMISSIONS);
        super.set(newContext != null ? newContext : defaultContext);
      }
    };
  }

  @Override
  public void checkPermission(Permission permission) {
    try {
      context.get().checkPermission(permission);
    } catch (AccessControlException ace) {
      throw new SecurityError("Missing required privileges", ace);
    }
  }

  @Override
  public void checkPermission(Permission permission, Object givenContext) {
    try {
      context.get().checkPermission(permission);
    } catch (AccessControlException ace) {
      throw new SecurityError("Missing required privileges", ace);
    }
  }

  public void changePermissions(final PermissionCollection permissions) {
    context.set(accessControlContextFor(permissions));
  }

}