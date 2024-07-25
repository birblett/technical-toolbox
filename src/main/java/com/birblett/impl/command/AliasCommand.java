package com.birblett.impl.command;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.alias.AliasManager;
import com.birblett.impl.alias.AliasedCommand;
import com.birblett.util.ServerUtil;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Alias command for adding, modifying, and removing aliases.
 */
public class AliasCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register((CommandManager.literal("alias")
                // adds an alias by name, with a given command
                .then(CommandManager.literal("add")
                        .requires(source -> source.hasPermissionLevel(4))
                        .then(CommandManager.argument("alias", StringArgumentType.string())
                                .then(CommandManager.argument("command", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String alias = context.getArgument("alias", String.class);
                                            ServerPlayerEntity player = context.getSource().getPlayer();
                                            if (dispatcher.getRoot().getChild(alias) == null) {
                                                String command = context.getArgument("command", String.class);
                                                new AliasedCommand(alias, command, dispatcher);
                                                Text out = TextUtils.formattable("Registered new command alias ")
                                                        .append(TextUtils.formattable(alias).formatted(Formatting.GREEN))
                                                        .append(" for command string ").append(TextUtils.formattable(
                                                                "\"" + command + "\"").formatted(Formatting.YELLOW));
                                                if (player != null) {
                                                    context.getSource().sendFeedback(() -> out, false);
                                                    context.getSource().getServer().sendMessage(TextUtils.formattable(player
                                                            .getNameForScoreboard() + ": ").append(out));
                                                } else {
                                                    context.getSource().sendFeedback(() -> out, false);
                                                }
                                                ServerUtil.refreshCommandTree(context.getSource().getServer());
                                                return 1;
                                            }
                                            if (player != null) {
                                                context.getSource().sendFeedback(() -> TextUtils.formattable("Couldn't " +
                                                        "register alias \"" + alias + "\" as such a command or alias " +
                                                        "already exists "), false);
                                            }
                                            return 0;
                                        }))))
                // removes an alias by name completely
                .then(CommandManager.literal("remove")
                        .requires(source -> source.hasPermissionLevel(4))
                        .then(CommandManager.argument("alias", StringArgumentType.string())
                                .suggests((context, builder) -> CommandSource.suggestMatching(AliasManager.ALIASES.keySet(), builder))
                                .executes(context -> {
                                    String alias = context.getArgument("alias", String.class);
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    AliasedCommand cmd = AliasManager.ALIASES.get(alias);
                                    if (cmd != null) {
                                        MutableText out = TextUtils.formattable("Removed command alias ").append(TextUtils
                                                .formattable(alias).formatted(Formatting.GREEN));
                                        cmd.deregister(context.getSource().getServer());
                                        context.getSource().sendFeedback(() -> out, false);
                                        if (player != null) {
                                            context.getSource().getServer().sendMessage(TextUtils.formattable(player
                                                    .getNameForScoreboard() + ": ").append(out));
                                        }
                                        return 1;
                                    }
                                    context.getSource().sendError(TextUtils.formattable("Couldn't find alias \"" +
                                            alias));
                                    return 0;
                                })))
                // reads all alias from file
                .then(CommandManager.literal("reload")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context -> {
                            TechnicalToolbox.ALIAS_MANAGER.readAliases();
                            context.getSource().sendFeedback(() -> TextUtils.formattable("Reloaded aliases from " +
                                    "disk"), false);
                            ServerUtil.refreshCommandTree(context.getSource().getServer());
                            return 1;
                        }))
                // writes all aliases to file
                .then(CommandManager.literal("save")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context -> {
                            TechnicalToolbox.ALIAS_MANAGER.writeAliases();
                            context.getSource().sendFeedback(() -> TextUtils.formattable("Wrote aliases to " +
                                    "disk"), false);
                            return 1;
                        }))
                // lists all aliases the executing player can use
                .then(CommandManager.literal("list")
                        .executes(context -> {
                            MutableText text = TextUtils.formattable("Aliases:");
                            for (AliasedCommand cmd : AliasManager.ALIASES.values()) {
                                if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() != null
                                        && context.getSource().getPlayer().hasPermissionLevel(cmd.getPermission())) {
                                    text.append("\n  " + cmd.getAlias() + ": ").append(cmd.getSyntax());
                                }
                            }
                            context.getSource().sendFeedback(() -> text, false);
                            return 1;
                        }))
                // modifies an existing alias
                .then(CommandManager.literal("modify")
                        .requires(source -> source.hasPermissionLevel(4))
                        .then(CommandManager.argument("alias", StringArgumentType.string())
                                .suggests((context, builder) -> CommandSource.suggestMatching(AliasManager.ALIASES.keySet(), builder))
                                // add a line to an alias
                                .then(CommandManager.literal("add")
                                        .then(CommandManager.argument("line", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    String alias = context.getArgument("alias", String.class);
                                                    AliasedCommand cmd = AliasManager.ALIASES.get(alias);
                                                    String command = context.getArgument("line", String.class);
                                                    if (cmd != null) {
                                                        cmd.addCommand(command);
                                                        MutableText out = TextUtils.formattable("Added line: \"")
                                                                .append(TextUtils.formattable(command).formatted(Formatting
                                                                .YELLOW)).append(TextUtils.formattable("\"\n"))
                                                                .append(cmd.getCommandText());
                                                        context.getSource().sendFeedback(() -> out, false);
                                                        return 1;
                                                    }
                                                    context.getSource().sendError(TextUtils.formattable("Couldn't " +
                                                            "find alias \"" + alias));
                                                    return 0;
                                                })))
                                // inserts a line to the alias at the given line
                                .then(CommandManager.literal("insert")
                                        .then(CommandManager.argument("line number", IntegerArgumentType.integer(1))
                                                .then(CommandManager.argument("line", StringArgumentType.greedyString())
                                                        .executes(context -> {
                                                            String alias = context.getArgument("alias", String.class);
                                                            int line = context.getArgument("line number", Integer.class);
                                                            AliasedCommand cmd = AliasManager.ALIASES.get(alias);
                                                            String command = context.getArgument("line", String.class);
                                                            if (cmd != null) {
                                                                MutableText err = cmd.insertCommand(command, line);
                                                                if (err != null) {
                                                                    context.getSource().sendError(err);
                                                                    return 0;
                                                                }
                                                                MutableText out = TextUtils.formattable("Inserted " +
                                                                                "command at line " + line + ": \"").append(TextUtils
                                                                                .formattable(command).formatted(Formatting.YELLOW))
                                                                        .append(TextUtils.formattable("\"\n")).append(
                                                                                cmd.getCommandText());
                                                                context.getSource().sendFeedback(() -> out, false);
                                                                return 1;
                                                            }
                                                            context.getSource().sendError(TextUtils.formattable("Couldn't " +
                                                                    "find alias \"" + alias));
                                                            return 0;
                                                        }))))
                                // replaces a specified line in the alias with another
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("line number", IntegerArgumentType.integer(1))
                                                .then(CommandManager.argument("line", StringArgumentType.greedyString())
                                                        .executes(context -> {
                                                            String alias = context.getArgument("alias", String.class);
                                                            int line = context.getArgument("line number", Integer.class);
                                                            AliasedCommand cmd = AliasManager.ALIASES.get(alias);
                                                            if (cmd != null) {
                                                                MutableText err;
                                                                String command = context.getArgument("line", String.class);
                                                                err = cmd.insertCommand(command, line);
                                                                if (err != null) {
                                                                    context.getSource().sendError(err);
                                                                    return 0;
                                                                }
                                                                err = cmd.removeCommand(line + 1);
                                                                if (err != null) {
                                                                    context.getSource().sendError(err);
                                                                    return 0;
                                                                }
                                                                MutableText out = TextUtils.formattable("Set " +
                                                                        "command at line " + line + ": \"").append(TextUtils
                                                                        .formattable(command).formatted(Formatting.YELLOW))
                                                                        .append(TextUtils.formattable("\"\n")).append(
                                                                        cmd.getCommandText());
                                                                context.getSource().sendFeedback(() -> out, false);
                                                                return 1;
                                                            }
                                                            context.getSource().sendError(TextUtils.formattable("Couldn't " +
                                                                    "find alias \"" + alias));
                                                            return 0;
                                                        }))))
                                // removes a specified line from the alias if there is more than one line
                                .then(CommandManager.literal("remove")
                                        .then(CommandManager.argument("line number", IntegerArgumentType.integer(1))
                                                .executes(context -> {
                                                    String alias = context.getArgument("alias", String.class);
                                                    int line = context.getArgument("line number", Integer.class);
                                                    AliasedCommand cmd = AliasManager.ALIASES.get(alias);
                                                    if (cmd != null) {
                                                        MutableText err;
                                                        err = cmd.removeCommand(line);
                                                        if (err != null) {
                                                            context.getSource().sendError(err);
                                                            return 0;
                                                        }
                                                        MutableText out = TextUtils.formattable("Removed command at " +
                                                                "line " + line + "\n").append(cmd.getCommandText());
                                                        context.getSource().sendFeedback(() -> out, false);
                                                        return 1;
                                                    }
                                                    context.getSource().sendError(TextUtils.formattable("Couldn't " +
                                                            "find alias \"" + alias));
                                                    return 0;
                                                })))
                                // sets the required permission level of the alias
                                .then(CommandManager.literal("permission")
                                        .then(CommandManager.argument("permission level", IntegerArgumentType.integer(1, 4))
                                                .executes(context -> {
                                                    String alias = context.getArgument("alias", String.class);
                                                    int permissionLevel = context.getArgument("permission level",
                                                            Integer.class);
                                                    AliasedCommand cmd = AliasManager.ALIASES.get(alias);
                                                    if (cmd != null) {
                                                        cmd.setPermission(permissionLevel);
                                                        context.getSource().sendFeedback(() -> TextUtils
                                                                .formattable("Permission level for alias ").append(TextUtils
                                                                .formattable(alias).formatted(Formatting.GREEN)).append(
                                                                TextUtils.formattable(" set to ").append(TextUtils
                                                                .formattable(String.valueOf(permissionLevel))
                                                                .formatted(Formatting.YELLOW))), false);
                                                        return 1;
                                                    }
                                                    context.getSource().sendError(TextUtils.formattable("Couldn't " +
                                                            "find alias \"" + alias));
                                                    return 0;
                                                })))
                                // sets the separator of the alias
                                .then(CommandManager.literal("separator")
                                        .then(CommandManager.argument("argument separator", StringArgumentType.string())
                                                .executes(context -> {
                                                    String alias = context.getArgument("alias", String.class);
                                                    String separator = context.getArgument("argument separator", String.class);
                                                    AliasedCommand cmd = AliasManager.ALIASES.get(alias);
                                                    if (cmd != null) {
                                                        cmd.setSeparator(separator);
                                                        context.getSource().sendFeedback(() -> TextUtils
                                                                .formattable("Argument separator for alias ").append(TextUtils
                                                                        .formattable(alias).formatted(Formatting.GREEN)).append(
                                                                        TextUtils.formattable(" set to \"").append(TextUtils
                                                                                .formattable(separator).formatted(Formatting.
                                                                                        YELLOW)).append("\"")), false);
                                                        return 1;
                                                    }
                                                    context.getSource().sendError(TextUtils.formattable("Couldn't " +
                                                            "find alias \"" + alias + "\n"));
                                                    return 0;
                                                })))
                                // sets the separator of the alias
                                .then(CommandManager.literal("silent")
                                        .then(CommandManager.argument("silent execution", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    String alias = context.getArgument("alias", String.class);
                                                    boolean silent = context.getArgument("silent execution", Boolean.class);
                                                    AliasedCommand cmd = AliasManager.ALIASES.get(alias);
                                                    if (cmd != null) {
                                                        cmd.setSilent(silent);
                                                        context.getSource().sendFeedback(() -> TextUtils
                                                                .formattable("Alias ").append(TextUtils
                                                                        .formattable(alias).formatted(Formatting.GREEN))
                                                                .append(TextUtils.formattable(" set to " + (silent ?
                                                                        "silent " : "verbose ") + "execution mode")),
                                                                false);
                                                        return 1;
                                                    }
                                                    context.getSource().sendError(TextUtils.formattable("Couldn't " +
                                                            "find alias \"" + alias + "\n"));
                                                    return 0;
                                                })))
                                // if called without subcommands will instead output alias information
                                .executes(context -> {
                                    String alias = context.getArgument("alias", String.class);
                                    AliasedCommand cmd = AliasManager.ALIASES.get(alias);
                                    if (cmd != null) {
                                        MutableText out = cmd.getCommandText();
                                        out.append(TextUtils.formattable("\nPermission level: ").append(TextUtils
                                                .formattable(String.valueOf(cmd.getPermission())).formatted(Formatting
                                                        .GREEN)));
                                        if (cmd.getArgCount() > 0) {
                                            out.append(TextUtils.formattable("\nArgument separator: \"").append(TextUtils
                                                    .formattable(String.valueOf(cmd.getSeparator())).formatted(Formatting
                                                            .GREEN)).append("\""));
                                            out.append("\nSyntax: ").append(cmd.getSyntax());
                                        }
                                        context.getSource().sendFeedback(() -> out, false);
                                        return 1;
                                    }
                                    context.getSource().sendError(TextUtils.formattable("Couldn't find alias \"" +
                                            alias));
                                    return 0;
                                })))));
    }

}
