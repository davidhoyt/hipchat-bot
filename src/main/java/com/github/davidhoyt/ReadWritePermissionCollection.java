package com.github.davidhoyt;

import java.security.Permission;
import java.security.PermissionCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;

public class ReadWritePermissionCollection extends PermissionCollection {

  private static final long serialVersionUID = 614300921365729272L;

  private final ArrayList<Permission> perms = new ArrayList<Permission>();

  public void add(Permission p) {
    perms.add(p);
  }

  public boolean implies(Permission p) {
    for (Iterator<Permission> i = perms.iterator(); i.hasNext(); ) {
      if (((Permission) i.next()).implies(p)) {
        return true;
      }
    }
    return false;
  }

  public Enumeration<Permission> elements() {
    return Collections.enumeration(perms);
  }

  public boolean isReadOnly() {
    return false;
  }

}
