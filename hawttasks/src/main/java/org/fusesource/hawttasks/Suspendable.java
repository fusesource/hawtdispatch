package org.fusesource.hawttasks;

public interface Suspendable extends Retained {
    public void suspend();
    public void resume();
}
