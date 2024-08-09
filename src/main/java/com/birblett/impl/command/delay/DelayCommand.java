package com.birblett.impl.command.delay;

import com.birblett.impl.config.ConfigOption;
import com.birblett.lib.command.delay.CommandOption;
import com.birblett.lib.command.delay.CommandScheduler;
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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
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
                                .redirect(node, context -> DelayCommand.optionalArgument(context, "source"))))
                .then(CommandManager.literal("priority")
                        .then(CommandManager.argument("priority", IntegerArgumentType.integer())
                                .redirect(node, context -> DelayCommand.optionalArgument(context, "priority"))))
                .then(CommandManager.literal("silent")
                        .then(CommandManager.argument("silent", BoolArgumentType.bool())
                                .redirect(node, context -> DelayCommand.optionalArgument(context, "silent"))))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .then(CommandManager.argument("delay", LongArgumentType.longArg(1))
                                        .then(CommandManager.argument("command", StringArgumentType.greedyString())
                                                .executes(DelayCommand::set)))))
                .then(CommandManager.literal("list")
                        .executes(DelayCommand::list))
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .executes(DelayCommand::remove))));
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

    private static ServerCommandSource optionalArgument(CommandContext<ServerCommandSource> context, String opt) {
        CommandOption s = (CommandOption) context.getSource();
        s.setOpt(opt, context.getArgument(opt, Boolean.class));
        return context.getSource();
    }

    private static int set(CommandContext<ServerCommandSource> context) {
        String source = DelayCommand.getOpt(context, "source", "self", String.class), id = context.getArgument("id",
                String.class);
        int priority = DelayCommand.getOpt(context, "priority", 1000, Integer.class);
        boolean silent = DelayCommand.getOpt(context, "silent", false, Boolean.class);
        long delay = context.getArgument("delay", Long.class);
        String command = context.getArgument("command", String.class);
        ((CommandOption) context.getSource()).resetOpt();
        MutableText out = ((CommandScheduler) context.getSource().getServer().getSaveProperties().getMainWorldProperties()
                .getScheduledEvents()).addCommandEvent(command, context.getSource().getWorld().getTime() + delay,
                        id, priority, silent, Objects.equals(source, "server") ? context.getSource().getServer().getCommandSource() :
                        context.getSource()) ? TextUtils.formattable("Scheduled command \"" + command + "\" with identifier ")
                .append(TextUtils.formattable(id).setStyle(Style.EMPTY.withColor(Formatting.GREEN))) : TextUtils.formattable("Command" +
                        " with identifier " + id + " already scheduled").setStyle(Style.EMPTY.withColor(Formatting.RED));
        context.getSource().sendFeedback(() -> out, false);
        return 0;
    }

    private static int list(CommandContext<ServerCommandSource> context) {
        CommandScheduler c = (CommandScheduler) context.getSource().getServer().getSaveProperties().getMainWorldProperties()
                .getScheduledEvents();
        List<Text> commandList = new ArrayList<>();
        for (String key : c.getCommandEventMap().keySet()) {
            CommandEvent event =  c.getCommandEventMap().get(key);
            long timeLeft = event.tick() - context.getSource().getServer().getSaveProperties().getMainWorldProperties().getTime();
            commandList.add(TextUtils.formattable(key).setStyle(Style.EMPTY.withColor(Formatting.GREEN)).append(TextUtils
                    .formattable(" | " + timeLeft + " | " + event.command()).setStyle(Style.EMPTY.withColor(Formatting.WHITE))));
        }
        for (Text cmd : commandList.reversed()) {
            context.getSource().sendFeedback(() -> cmd, false);
        }
        return 0;
    }

    private static int remove(CommandContext<ServerCommandSource> context) {
        CommandScheduler c = (CommandScheduler) context.getSource().getServer().getSaveProperties().getMainWorldProperties()
                .getScheduledEvents();
        MutableText out;
        String id = context.getArgument("id", String.class);
        if (c.removeCommandEvent(id)) {
            out = TextUtils.formattable("Removed scheduled command with identifier ").append(TextUtils.formattable(id)
                    .setStyle(Style.EMPTY.withColor(Formatting.GREEN)));
        }
        else {
            out = TextUtils.formattable("No scheduled command with identifier \"" + id + "\"").setStyle(Style.EMPTY
                    .withColor(Formatting.RED));
        }
        context.getSource().sendFeedback(() -> out, false);
        return 0;
    }

}
