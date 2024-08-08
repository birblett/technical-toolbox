package com.birblett.impl.delay;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.config.ConfigOption;
import com.birblett.lib.delay.CommandOption;
import com.birblett.lib.delay.CommandScheduler;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Objects;

/**
 * Simple non-serialized command scheduler.
 */
public class DelayCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralCommandNode<ServerCommandSource> node = dispatcher.register(CommandManager.literal(ConfigOption
                        .DELAY_COMMAND.val())
                .requires(source -> source.hasPermissionLevel(4)));
        dispatcher.register(CommandManager.literal(ConfigOption
                        .DELAY_COMMAND.val())
                .requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.literal("as")
                        .requires(ServerCommandSource::isExecutedByPlayer)
                        .then(CommandManager.argument("source", StringArgumentType.string())
                                .suggests((context, builder) -> CommandSource.suggestMatching(List.of("self", "server"),
                                        builder))
                                .redirect(node, context -> {
                                    CommandOption s = (CommandOption) context.getSource();
                                    s.setOpt("source", context.getArgument("source", String.class));
                                    return context.getSource();
                                })))
                .then(CommandManager.literal("priority")
                        .then(CommandManager.argument("priority", IntegerArgumentType.integer())
                                .redirect(node, context -> {
                                    CommandOption s = (CommandOption) context.getSource();
                                    s.setOpt("priority", context.getArgument("priority", Integer.class));
                                    return context.getSource();
                                })))
                .then(CommandManager.literal("silent")
                        .then(CommandManager.argument("silent", BoolArgumentType.bool())
                                .redirect(node, context -> {
                                    CommandOption s = (CommandOption) context.getSource();
                                    s.setOpt("silent", context.getArgument("silent", Boolean.class));
                                    TechnicalToolbox.log("gwa");
                                    return context.getSource();
                                })))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("id", StringArgumentType.string())
                                .then(CommandManager.argument("delay", LongArgumentType.longArg(1))
                                        .then(CommandManager.argument("command", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String source = DelayCommand.getOpt(context, "source", "server", String.class),
                                                            id = context.getArgument("id", String.class);
                                                    int priority = DelayCommand.getOpt(context, "priority", 1000, Integer.class);
                                                    boolean silent = DelayCommand.getOpt(context, "silent", true, Boolean.class);
                                                    long delay = context.getArgument("delay", Long.class);
                                                    String command = context.getArgument("command", String.class);
                                                    ((CommandOption) context.getSource()).resetOpt();
                                                    MutableText out = ((CommandScheduler) context.getSource().getServer()
                                                            .getSaveProperties().getMainWorldProperties().getScheduledEvents())
                                                            .addCommandEvent(command, context.getSource().getWorld().getTime() + delay,
                                                            id, priority, silent, Objects.equals(source, "server") ? context.getSource()
                                                            .getServer().getCommandSource() : context.getSource()) ? TextUtils.formattable(
                                                            "Scheduled command \"" + command + "\" with identifier ").append(TextUtils
                                                            .formattable(id).setStyle(Style.EMPTY.withColor(Formatting.GREEN))) : TextUtils
                                                            .formattable("Command with identifier " + id + " already scheduled")
                                                            .setStyle(Style.EMPTY.withColor(Formatting.RED));
                                                    context.getSource().sendFeedback(() -> out, false);
                                                    return 0;
                                                }))))));
    }

    private static <T> T getOpt(CommandContext<ServerCommandSource> context, String arg, T def, Class<T> clazz) {
        CommandOption c = (CommandOption) context.getSource();
        try {
            Object o = c.getOpt(arg);
            return o == null ? def : clazz.cast(o);
        }
        catch (ClassCastException e) {
            return def;
        }
    }

}
