package com.birblett.lib.command.delay;

import com.birblett.impl.command.delay.CommandEvent;
import net.minecraft.server.command.ServerCommandSource;

import java.util.HashMap;

public interface CommandScheduler {

    boolean technicalToolbox$AddCommandEvent(String command, long delay, String id, int priority, boolean silent, ServerCommandSource source);
    boolean technicalToolbox$RemoveCommandEvent(String id);
    HashMap<String, CommandEvent> technicalToolbox$GetCommandEventMap();

}
