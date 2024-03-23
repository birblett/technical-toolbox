package com.birblett.impl.command;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.config.ConfigOptions;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;

/**
 * brigadier forces me to use 10 billion nested calls and lambdas so decipher it yourself lmao
 */
public class ToolboxCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("toolbox")
                .requires(source -> source.hasPermissionLevel((Integer) ConfigOptions.CONFIG_VIEW_PERMISSION_LEVEL.value()))
                .then(CommandManager.literal("config")
                        .then(CommandManager.argument("config_option", StringArgumentType.string())
                                .requires(source -> source.hasPermissionLevel(4))
                                .suggests((context, builder) -> CommandSource.suggestMatching(TechnicalToolbox.CONFIG_MANAGER
                                        .getAllConfigOptions(), builder))
                                .then(CommandManager.argument("config_value", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            Collection<String> suggestions = new ArrayList<>();
                                            String tmp = context.getArgument("config_option", String.class);
                                            if (TechnicalToolbox.CONFIG_MANAGER.getAllConfigOptions().contains(tmp)) {
                                                ConfigOptions c = TechnicalToolbox.CONFIG_MANAGER.configMap.get(tmp);
                                                if (c != null) {
                                                    suggestions =  c.commandSuggestions();
                                                }
                                            }
                                            return CommandSource.suggestMatching(suggestions, builder);
                                        })
                                        .executes((context -> {
                                            String option = context.getArgument("config_option", String.class);
                                            String value = context.getArgument("config_value", String.class);
                                            if (TechnicalToolbox.CONFIG_MANAGER.getAllConfigOptions().contains(option)) {
                                                ConfigOptions c = TechnicalToolbox.CONFIG_MANAGER.configMap.get(option);
                                                Text out = c.setFromString(value, context.getSource().getServer());
                                                if (out != null) {
                                                    context.getSource().sendFeedback(() -> out, false);
                                                }
                                                else {
                                                    context.getSource().sendFeedback(() -> TextUtils
                                                            .formattable("Successfully set value ").append(TextUtils
                                                            .formattable(value).setStyle(Style.EMPTY.withColor(Formatting
                                                            .GREEN))).append(TextUtils.formattable(" for option " + option)),
                                                            true);
                                                    if ((Boolean) ConfigOptions.CONFIG_WRITE_ON_CHANGE.value()) {
                                                        TechnicalToolbox.CONFIG_MANAGER.writeConfigsToFile();
                                                    }
                                                }
                                            }
                                            return 1;
                                        })))
                                .executes(context -> {
                                    String option = context.getArgument("config_option", String.class);
                                    if (TechnicalToolbox.CONFIG_MANAGER.getAllConfigOptions().contains(option)) {
                                        ConfigOptions c = TechnicalToolbox.CONFIG_MANAGER.configMap.get(option);
                                        context.getSource().sendFeedback(c::getText, true);
                                    }
                                    return 1;
                                })))
                .then(CommandManager.literal("reload_configs")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context -> {
                            context.getSource().sendFeedback(() -> TextUtils.formattable("Reloading configs from disk"),
                                    true);
                            TechnicalToolbox.CONFIG_MANAGER.readConfigsFromFile();
                            return 1;
                        }))
                .then(CommandManager.literal("write_configs")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context -> {
                            context.getSource().sendFeedback(() -> TextUtils.formattable("Writing configs to disk"),
                                    true);
                            TechnicalToolbox.CONFIG_MANAGER.writeConfigsToFile();
                            return 1;
                        })));

    }

}