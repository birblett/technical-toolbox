package com.birblett.impl.command.delay;

import com.birblett.accessor.command.delay.AliasedCommandSource;
import com.birblett.accessor.command.delay.CommandScheduler;
import com.birblett.impl.config.ConfigOptions;
import com.birblett.util.ServerUtil;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Simple non-serialized command scheduler.
 */
public class DelayCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(Commands.literal(ConfigOptions
                        .DELAY_COMMAND.val())
                .requires(ServerUtil::hasAdminPerms));
        dispatcher.register(Commands.literal(ConfigOptions
                        .DELAY_COMMAND.val())
                .requires(ServerUtil::hasAdminPerms)
                .then(Commands.literal("as")
                        .then(Commands.argument("source", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().isPlayer() ?
                                                List.of("self", "server") : List.of("server"), builder))
                                .redirect(node, context -> DelayCommand.optionalArgument(context, "source", String.class))))
                .then(Commands.literal("priority")
                        .then(Commands.argument("priority", IntegerArgumentType.integer())
                                .redirect(node, context -> DelayCommand.optionalArgument(context, "priority", Integer.class))))
                .then(Commands.literal("silent")
                        .then(Commands.argument("silent", BoolArgumentType.bool())
                                .redirect(node, context -> DelayCommand.optionalArgument(context, "silent", Boolean.class))))
                .then(Commands.literal("set")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("delay", LongArgumentType.longArg(1))
                                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                                .executes(DelayCommand::set)))))
                .then(Commands.literal("proc")
                        .then(Commands.argument("delay", LongArgumentType.longArg(1))
                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                        .executes(DelayCommand::proc))))
                .then(Commands.literal("list")
                        .executes(DelayCommand::list))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(DelayCommand::getIdSuggestions)
                                .executes(DelayCommand::remove))));
    }

    private static <T> T getOpt(CommandContext<CommandSourceStack> context, String arg, T def, Class<T> clazz) {
        AliasedCommandSource c = (AliasedCommandSource) context.getSource();
        try {
            Object o = c.technicalToolbox$GetOpt(arg);
            return o == null ? def : clazz.cast(o);
        } catch (ClassCastException e) {
            return def;
        }
    }

    private static CommandSourceStack optionalArgument(CommandContext<CommandSourceStack> context, String opt, Class<?> clazz) {
        AliasedCommandSource s = (AliasedCommandSource) context.getSource();
        s.technicalToolbox$SetOpt(opt, context.getArgument(opt, clazz));
        return context.getSource();
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        String source = DelayCommand.getOpt(context, "source", "self", String.class), id = context.getArgument("id",
                String.class);
        int priority = DelayCommand.getOpt(context, "priority", 1000, Integer.class);
        boolean silent = DelayCommand.getOpt(context, "silent", false, Boolean.class);
        long delay = context.getArgument("delay", Long.class);
        String command = context.getArgument("command", String.class);
        ((AliasedCommandSource) context.getSource()).technicalToolbox$ResetOpt();
        MutableComponent out = ((CommandScheduler) context.getSource().getServer().getWorldData().overworldData()
                .getScheduledEvents()).technicalToolbox$AddCommandEvent(command, context.getSource().getLevel().getGameTime() + delay,
                id, priority, silent, Objects.equals(source, "server") ? context.getSource().getServer().createCommandSourceStack() :
                        context.getSource()) ? TextUtils.formattable("Scheduled command \"" + command + "\" with identifier ")
                .append(TextUtils.formattable(id).setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN))) : TextUtils.formattable("Command" +
                " with identifier " + id + " already scheduled").setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        context.getSource().sendSuccess(() -> out, false);
        return 0;
    }

    private static int proc(CommandContext<CommandSourceStack> context) {
        String source = DelayCommand.getOpt(context, "source", "self", String.class);
        int priority = DelayCommand.getOpt(context, "priority", 1000, Integer.class);
        boolean silent = DelayCommand.getOpt(context, "silent", false, Boolean.class);
        long delay = context.getArgument("delay", Long.class);
        String command = context.getArgument("command", String.class);
        ((AliasedCommandSource) context.getSource()).technicalToolbox$ResetOpt();
        ((CommandScheduler) context.getSource().getServer().getWorldData().overworldData().getScheduledEvents())
                .technicalToolbox$AddCommandEvent(command, context.getSource().getLevel().getGameTime() + delay,null, priority, silent,
                        Objects.equals(source, "server") ? context.getSource().getServer().createCommandSourceStack() : context.getSource());
        context.getSource().sendSuccess(() -> TextUtils.formattable("Scheduled command \"" + command + "\" with " + delay +
                " game tick delay"), false);
        return 0;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandScheduler c = (CommandScheduler) context.getSource().getServer().getWorldData().overworldData()
                .getScheduledEvents();
        List<Component> commandList = new ArrayList<>();
        for (String key : c.technicalToolbox$GetCommandEventMap().keySet()) {
            CommandEvent event = c.technicalToolbox$GetCommandEventMap().get(key);
            long timeLeft = event.tick() - context.getSource().getServer().getWorldData().overworldData().getGameTime();
            commandList.add(TextUtils.formattable(key).setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)).append(TextUtils
                    .formattable(" | " + timeLeft + " | " + event.command()).setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE))));
        }
        for (Component cmd : commandList.reversed()) {
            context.getSource().sendSuccess(() -> cmd, false);
        }
        return 0;
    }

    private static CompletableFuture<Suggestions> getIdSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(((CommandScheduler) context.getSource().getServer().getWorldData()
                .overworldData().getScheduledEvents()).technicalToolbox$GetCommandEventMap().keySet(), builder);
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        CommandScheduler c = (CommandScheduler) context.getSource().getServer().getWorldData().overworldData()
                .getScheduledEvents();
        MutableComponent out;
        String id = context.getArgument("id", String.class);
        if (c.technicalToolbox$RemoveCommandEvent(id)) {
            out = TextUtils.formattable("Removed scheduled command with identifier ").append(TextUtils.formattable(id)
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)));
        } else {
            out = TextUtils.formattable("No scheduled command with identifier \"" + id + "\"").setStyle(Style.EMPTY
                    .withColor(ChatFormatting.RED));
        }
        context.getSource().sendSuccess(() -> out, false);
        return 0;
    }

}
