package org.fusesource.hawttasks;

public interface Suspendable extends RefCounted {
    public void suspend();
    public void resume();
}
