package com.birblett.lib.command;

public interface CommandScheduler {

    void addCommandEvent(String command, long delay, int priority, boolean silent);
    default void addCommandEvent(String command, long delay) {
        this.addCommandEvent(command, delay, 1000, false);
    }

}
