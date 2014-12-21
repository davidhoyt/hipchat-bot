package com.github.davidhoyt;

import java.security.*;
import java.security.cert.Certificate;

//Courtesy https://github.com/alphaloop/selective-security-manager

public class SandboxSecurityManager extends SecurityManager {
    private static final RuntimePermission TOGGLE_SECURITY_MANAGER_PERMISSION = new RuntimePermission("toggleSecurityManager");
    private static final RuntimePermission CREATE_THREAD_PERMISSION = new RuntimePermission("createThread");

    InheritableThreadLocal<Boolean> enabledFlag = null;
    InheritableThreadLocal<AccessControlContext> context = null;

    public SandboxSecurityManager(final boolean enabledByDefault, final PermissionCollection defaultPermissions) {
        final ProtectionDomain domain = new ProtectionDomain(new CodeSource(null, (Certificate[])null), defaultPermissions);
        final AccessControlContext givenContext = new AccessControlContext(new ProtectionDomain[] { domain });

        context = new InheritableThreadLocal<AccessControlContext>() {
            @Override
            protected AccessControlContext initialValue() {
                return givenContext;
            }

            @Override
            protected AccessControlContext childValue(AccessControlContext parentContext) {
                checkPermission(CREATE_THREAD_PERMISSION);
                return parentContext;
            }
        };

        enabledFlag = new InheritableThreadLocal<Boolean>() {
            @Override
            protected Boolean initialValue() {
                return enabledByDefault;
            }

            @Override
            protected Boolean childValue(Boolean parentValue) {
                return parentValue;
            }

            @Override
            public void set(Boolean value) {
                checkPermission(TOGGLE_SECURITY_MANAGER_PERMISSION);
                super.set(value);
            }
        };
    }

    @Override
    public void checkPermission(Permission permission) {
        if (shouldCheck(permission)) {
            try {
                context.get().checkPermission(permission);
            } catch(AccessControlException ace) {
                throw new SecurityError("Missing required privileges", ace);
            }
        }
    }

    @Override
    public void checkPermission(Permission permission, Object givenContext) {
        if (shouldCheck(permission)) {
            try {
                context.get().checkPermission(permission);
            } catch(AccessControlException ace) {
                throw new SecurityError("Missing required privileges", ace);
            }
        }
    }

    private boolean shouldCheck(Permission permission) {
        return isEnabled();
    }

    public void enable() {
        enabledFlag.set(true);
    }

    public void disable() {
        enabledFlag.set(false);
    }

    public boolean isEnabled() {
        return enabledFlag.get();
    }

}