package com.birblett.impl.command;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.config.ConfigOption;
import com.birblett.impl.config.ConfigOptions;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Ingame command for viewing and modifying configuration options
 */
public class ToolboxCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("toolbox")
                .requires(source -> source.hasPermissionLevel(ConfigOptions.CONFIG_VIEW_PERMISSION_LEVEL.val()))
                .then(CommandManager.literal("config")
                        .then(CommandManager.argument("config_option", StringArgumentType.string())
                                .suggests((context, builder) -> CommandSource.suggestMatching(TechnicalToolbox.CONFIG_MANAGER
                                        .getAllConfigOptions(), builder))
                                // modifying configs; requires admin perms
                                .then(CommandManager.argument("config_value", StringArgumentType.string())
                                        .requires(source -> source.hasPermissionLevel(4))
                                        .suggests(ToolboxCommand::configSuggestions)
                                        .executes((ToolboxCommand::set)))
                                .executes(ToolboxCommand::get)))
                // force-reloads configs from storage
                .then(CommandManager.literal("reload_configs")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(ToolboxCommand::reload))
                // force-writes configs to storage
                .then(CommandManager.literal("save_configs")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(ToolboxCommand::save)));

    }

    private static CompletableFuture<Suggestions> configSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        Collection<String> suggestions = new ArrayList<>();
        String tmp = context.getArgument("config_option", String.class);
        if (TechnicalToolbox.CONFIG_MANAGER.getAllConfigOptions().contains(tmp)) {
            ConfigOption<?> c = TechnicalToolbox.CONFIG_MANAGER.configMap.get(tmp);
            if (c != null) {
                suggestions =  c.commandSuggestions();
            }
        }
        return CommandSource.suggestMatching(suggestions, builder);
    }

    private static int set(CommandContext<ServerCommandSource> context) {
        String option = context.getArgument("config_option", String.class);
        String value = context.getArgument("config_value", String.class);
        if (TechnicalToolbox.CONFIG_MANAGER.getAllConfigOptions().contains(option)) {
            ConfigOption<?> c = TechnicalToolbox.CONFIG_MANAGER.configMap.get(option);
            Text out = c.setFromString(value, context.getSource().getServer());
            if (out != null) {
                context.getSource().sendError(out);
                return 0;
            }
            else {
                context.getSource().sendFeedback(() -> TextUtils.formattable("Successfully set value ").append(
                        TextUtils.formattable(value).setStyle(Style.EMPTY.withColor(Formatting.GREEN))).append(
                                TextUtils.formattable(" for option " + option)), true);
                if (ConfigOptions.CONFIG_WRITE_ON_CHANGE.val()) {
                    TechnicalToolbox.CONFIG_MANAGER.writeConfigs(context.getSource().getServer());
                }
                return 1;
            }
        }
        else {
            context.getSource().sendError(TextUtils.formattable("No config option with name \"" + option + "\""));
            return 0;
        }
    }

    private static int get(CommandContext<ServerCommandSource> context) {
        String option = context.getArgument("config_option", String.class);
        if (TechnicalToolbox.CONFIG_MANAGER.getAllConfigOptions().contains(option)) {
            ConfigOption<?> c = TechnicalToolbox.CONFIG_MANAGER.configMap.get(option);
            context.getSource().sendFeedback(c::getText, true);
        }
        else {
            context.getSource().sendError(TextUtils.formattable("No config option with name \"" + option + "\""));
        }
        return 1;
    }

    private static int reload(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(() -> TextUtils.formattable("Reloading configs..."), true);
        TechnicalToolbox.CONFIG_MANAGER.readConfigs(context.getSource().getServer());
        return 1;
    }

    private static int save(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(() -> TextUtils.formattable("Saving configs..."), true);
        TechnicalToolbox.CONFIG_MANAGER.writeConfigs(context.getSource().getServer());
        return 1;
    }

}
