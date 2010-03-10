package org.fusesource.hawtdispatch;

public interface Suspendable extends Retained {
    public void suspend();
    public void resume();
    public boolean isSuspended();
}
