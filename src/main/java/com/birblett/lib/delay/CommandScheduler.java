package com.birblett.lib.delay;

import net.minecraft.server.command.ServerCommandSource;

public interface CommandScheduler {

    boolean addCommandEvent(String command, long delay, String id, int priority, boolean silent, ServerCommandSource source);

}
