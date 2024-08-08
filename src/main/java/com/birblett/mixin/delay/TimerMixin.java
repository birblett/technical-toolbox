package com.birblett.mixin.delay;

import com.birblett.impl.delay.CommandEvent;
import com.birblett.lib.delay.CommandScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.timer.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.PriorityQueue;

@Mixin(Timer.class)
public class TimerMixin<T> implements CommandScheduler {

    @Unique private final PriorityQueue<CommandEvent> scheduledCommands = new PriorityQueue<>((a, b) -> a.tick() - b
            .tick() == 0 ? a.priority() - b.priority() : a.tick() > b.tick() ? 1 : -1);

    @Unique private final HashMap<String, CommandEvent> scheduledCommandMap = new HashMap<>();

    @Override
    public boolean addCommandEvent(String command, long delay, String id, int priority, boolean silent, ServerCommandSource source) {
        if (!this.scheduledCommandMap.containsKey(id)) {
            CommandEvent e = new CommandEvent(id, command, delay, priority, silent, source);
            this.scheduledCommands.add(e);
            this.scheduledCommandMap.put(id, e);
            return true;
        }
        return false;
    }

    @Inject(method = "processEvents", at = @At("TAIL"))
    private void executeSchedule(T server, long time, CallbackInfo ci) {
        if (server instanceof MinecraftServer s) {
            while (this.scheduledCommands.peek() != null && this.scheduledCommands.peek().tick() <= time) {
                CommandEvent e = this.scheduledCommands.remove();
                this.scheduledCommandMap.remove(e.id());
                e.execute(s);
            }
        }
    }

}
