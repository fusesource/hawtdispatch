/**
 * Copyright (C) 2010, FuseSource Corp.  All rights reserved.
 */
package org.fusesource.hawtdispatch;

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
final public class TaskWrapper extends Task {

    private final Runnable runnable;

    public TaskWrapper(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public void run() {
        runnable.run();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskWrapper that = (TaskWrapper) o;
        if (runnable != null ? !runnable.equals(that.runnable) : that.runnable != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return runnable != null ? runnable.hashCode() : 0;
    }

    @Override
    public String toString() {
        return runnable.toString();
    }
}
