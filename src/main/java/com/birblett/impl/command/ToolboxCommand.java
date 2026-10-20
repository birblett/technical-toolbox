package com.birblett.impl.command;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.config.ConfigOption;
import com.birblett.impl.config.ConfigOptions;
import com.birblett.util.ServerUtil;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Ingame command for viewing and modifying configuration options
 */
public class ToolboxCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("toolbox")
                .requires(source -> ServerUtil.permissionLookup(ConfigOptions.CONFIG_VIEW_PERMISSION_LEVEL.val(), source))
                .then(Commands.literal("config")
                        .then(Commands.argument("config_option", StringArgumentType.string())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(TechnicalToolbox.CONFIG_MANAGER.getAllConfigOptions(), builder))
                                // modifying configs; requires admin perms
                                .then(Commands.argument("config_value", StringArgumentType.string())
                                        .requires(ServerUtil::hasAdminPerms)
                                        .suggests(ToolboxCommand::configSuggestions)
                                        .executes((ToolboxCommand::set)))
                                .executes(ToolboxCommand::get)))
                // force-reloads configs from storage
                .then(Commands.literal("reload_configs")
                        .requires(ServerUtil::hasAdminPerms)
                        .executes(ToolboxCommand::reload))
                // force-writes configs to storage
                .then(Commands.literal("save_configs")
                        .requires(ServerUtil::hasAdminPerms)
                        .executes(ToolboxCommand::save)));

    }

    public static CompletableFuture<Suggestions> configSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        Collection<String> suggestions = new ArrayList<>();
        String tmp = context.getArgument("config_option", String.class);
        if (TechnicalToolbox.CONFIG_MANAGER.getAllConfigOptions().contains(tmp)) {
            ConfigOption<?> c = TechnicalToolbox.CONFIG_MANAGER.get(tmp);
            if (c != null) {
                suggestions = c.commandSuggestions();
            }
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        String option = context.getArgument("config_option", String.class);
        String value = context.getArgument("config_value", String.class);
        if (TechnicalToolbox.CONFIG_MANAGER.getAllConfigOptions().contains(option)) {
            ConfigOption<?> c = TechnicalToolbox.CONFIG_MANAGER.get(option);
            Component out = c.setFromString(value, context.getSource().getServer());
            if (out != null) {
                context.getSource().sendFailure(out);
                return 0;
            } else {
                context.getSource().sendSuccess(() -> TextUtils.formattable("Successfully set value ").append(
                        TextUtils.formattable(value).setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN))).append(
                        TextUtils.formattable(" for option " + option)), true);
                if (ConfigOptions.CONFIG_WRITE_ON_CHANGE.val()) {
                    TechnicalToolbox.CONFIG_MANAGER.writeConfigs(ServerUtil.getToolboxPath(context.getSource().getServer()));
                }
                return 1;
            }
        } else {
            context.getSource().sendFailure(TextUtils.formattable("No config option with name \"" + option + "\""));
            return 0;
        }
    }

    private static int get(CommandContext<CommandSourceStack> context) {
        String option = context.getArgument("config_option", String.class);
        if (TechnicalToolbox.CONFIG_MANAGER.getAllConfigOptions().contains(option)) {
            ConfigOption<?> c = TechnicalToolbox.CONFIG_MANAGER.get(option);
            context.getSource().sendSuccess(c::getText, true);
        } else {
            context.getSource().sendFailure(TextUtils.formattable("No config option with name \"" + option + "\""));
        }
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> TextUtils.formattable("Reloading configs..."), true);
        MinecraftServer server = context.getSource().getServer();
        TechnicalToolbox.CONFIG_MANAGER.readConfigs(ServerUtil.getWorldPath(server), server);
        context.getSource().sendSuccess(() -> TextUtils.formattable("Done."), true);
        return 1;
    }

    private static int save(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> TextUtils.formattable("Saving configs..."), true);
        TechnicalToolbox.CONFIG_MANAGER.writeConfigs(ServerUtil.getToolboxPath(context.getSource().getServer()));
        context.getSource().sendSuccess(() -> TextUtils.formattable("Done."), true);
        return 1;
    }

}
