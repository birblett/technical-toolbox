package com.birblett.mixin.scheduler;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.schedule.CommandEvent;
import com.birblett.lib.command.CommandScheduler;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.PriorityQueue;

@Mixin(ServerWorld.class)
public class ServerWorldMixin implements CommandScheduler {

    @Unique private final PriorityQueue<CommandEvent> SCHEDULED_EVENTS = new PriorityQueue<>((a, b) -> a.tick() - b
            .tick() == 0 ? a.priority() - b.priority() : a.tick() > b.tick() ? 1 : -1);

    @Override
    public void addCommandEvent(String command, long delay, int priority, boolean silent) {
        SCHEDULED_EVENTS.add(new CommandEvent(command, delay + ((ServerWorld) (Object) this).getTime(), priority, silent));
    }

    @Inject(method = "tickTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerWorldProperties;getScheduledEvents()Lnet/minecraft/world/timer/Timer;"))
    private void executeSchedule(CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        while (SCHEDULED_EVENTS.peek() != null && SCHEDULED_EVENTS.peek().tick() <= world.getTime()) {
            SCHEDULED_EVENTS.remove().execute(world.getServer());
        }
    }
}
