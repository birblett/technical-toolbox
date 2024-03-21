package com.birblett.command;

import com.birblett.TechnicalToolbox;
import com.birblett.util.config.ConfigOptions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

/**
 * brigadier forces me to use 10 billion nested calls so idfk read this yourself lmao
 */
public class ToolboxCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandManager m) {
        dispatcher.register((CommandManager.literal("toolbox")
                .requires(source -> source.hasPermissionLevel(2)))
                .then(CommandManager.literal("config")
                        .then(CommandManager.argument("config_option", StringArgumentType.string())
                                .suggests((context, builder) -> CommandSource.suggestMatching(TechnicalToolbox.CONFIG_MANAGER.getAllConfigOptions(), builder))
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
                                                String out = c.setFromString(value, context.getSource().getServer());
                                                context.getSource().sendFeedback(() -> Text.of(Objects.requireNonNullElseGet(
                                                        out, () -> "Successfully set value '" + value + "' for option "
                                                                + option)), true);
                                            }
                                            return 1;
                                        })))))
                .then(CommandManager.literal("reload_configs")
                        .executes((context -> {
                            context.getSource().sendFeedback(() -> Text.of("Reloading configs from disk"), true);
                            TechnicalToolbox.CONFIG_MANAGER.readConfigsFromFile();
                            return 1;
                        })))
                .then(CommandManager.literal("write_configs")
                        .executes((context -> {
                            context.getSource().sendFeedback(() -> Text.of("Writing configs to disk"), true);
                            TechnicalToolbox.CONFIG_MANAGER.writeConfigsToFile();
                            return 1;
                        }))));

    }

}
