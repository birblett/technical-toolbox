package com.birblett.impl.command.alias;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.command.alias.language.Variable;
import com.birblett.impl.config.ConfigOptions;
import com.birblett.util.ServerUtil;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Alias command for adding, modifying, and removing aliases.
 */
public class AliasCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register((CommandManager.literal("alias")
                // adds an alias by name, with a given command
                .then(CommandManager.literal("add")
                        .requires(source -> source.hasPermissionLevel(4))
                        .then(CommandManager.argument("alias", StringArgumentType.word())
                                .then(CommandManager.argument("command", StringArgumentType.greedyString())
                                        .executes(AliasCommand::add))))
                // removes an alias by name completely
                .then(CommandManager.literal("remove")
                        .requires(source -> source.hasPermissionLevel(4))
                        .then(CommandManager.argument("alias", StringArgumentType.word())
                                .suggests(AliasCommand::listAliases)
                                .executes(AliasCommand::remove)))
                // reads all alias from file
                .then(CommandManager.literal("reload")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(AliasCommand::reload))
                // writes all aliases to file
                .then(CommandManager.literal("save")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(AliasCommand::save))
                // lists all aliases the executing player can use
                .then(CommandManager.literal("list")
                        .executes(AliasCommand::list))
                // attemps to compile a specific alias
                .then(CommandManager.literal("compile")
                        .requires(source -> true)
                        .then(CommandManager.argument("alias", StringArgumentType.word())
                                .suggests(AliasCommand::listAliases)
                                .executes(AliasCommand::compile)))
                // modifies an existing alias
                .then(CommandManager.literal("modify")
                        .requires(source -> source.hasPermissionLevel(4))
                        .then(CommandManager.argument("alias", StringArgumentType.word())
                                .suggests(AliasCommand::listAliases)
                                // add a line to an alias
                                .then(CommandManager.literal("add")
                                        .then(CommandManager.argument("line", StringArgumentType.greedyString())
                                                .executes(AliasCommand::modifyAdd)))
                                // inserts a line to the alias at the given line
                                .then(CommandManager.literal("insert")
                                        .then(CommandManager.argument("line number", IntegerArgumentType.integer(1))
                                                .then(CommandManager.argument("line", StringArgumentType.greedyString())
                                                        .executes(AliasCommand::modifyInsert))))
                                // replaces a specified line in the alias with another
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("line number", IntegerArgumentType.integer(1))
                                                .then(CommandManager.argument("line", StringArgumentType.greedyString())
                                                        .suggests(AliasCommand::modifyLineSuggestion)
                                                        .executes(AliasCommand::modifySet))))
                                // removes a specified line from the alias if there is more than one line
                                .then(CommandManager.literal("remove")
                                        .then(CommandManager.argument("line number", IntegerArgumentType.integer(1))
                                                .executes(AliasCommand::modifyRemove)))
                                // renames an alias to a specified string
                                .then(CommandManager.literal("rename")
                                        .then(CommandManager.argument("name", StringArgumentType.word())
                                                .executes(AliasCommand::modifyRename)))
                                // sets the required permission level of the alias
                                .then(CommandManager.literal("permission")
                                        .then(CommandManager.argument("permission level", IntegerArgumentType.integer(1, 4))
                                                .executes(AliasCommand::modifyPermission)))
                                // sets the verbosity of the alias
                                .then(CommandManager.literal("silent")
                                        .then(CommandManager.argument("silent execution", BoolArgumentType.bool())
                                                .executes(AliasCommand::modifySilent)))
                                // edit arguments of the alias
                                .then(CommandManager.literal("argument")
                                        // add an argument without replacing it
                                        .then(AliasCommand.addOrSetArguments("add", false))
                                        // set an argument, replacing any existing one
                                        .then(AliasCommand.addOrSetArguments("set", true))
                                        // remove an argument
                                        .then(CommandManager.literal("remove")
                                                .then(CommandManager.argument("argument", StringArgumentType.word())
                                                        .suggests(AliasCommand::modifyListArguments)
                                                        .executes(AliasCommand::modifyArgumentRemove)))
                                        // rename an argument
                                        .then(CommandManager.literal("rename")
                                                .then(CommandManager.argument("argument", StringArgumentType.word())
                                                        .suggests(AliasCommand::modifyListArguments)
                                                        .then(CommandManager.argument("newArgument", StringArgumentType.word())
                                                            .executes(AliasCommand::modifyArgumentRename)))))
                                // if called without subcommands will instead output alias information
                                .executes(AliasCommand::modifyInfo)))));
    }

    /**
     * Registers an alias with a single command.
     */
    private static int add(CommandContext<ServerCommandSource> context) {
        String alias = context.getArgument("alias", String.class);
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (context.getRootNode().getChild(alias) == null) {
            String command = context.getArgument("command", String.class);
            new AliasedCommand(alias, command, context.getSource().getDispatcher());
            Text out = TextUtils.formattable("Registered new command alias ").append(TextUtils.formattable(alias)
                            .formatted(Formatting.GREEN)).append(" for command string ").append(TextUtils.formattable("\"" + command +
                            "\"").formatted(Formatting.YELLOW));
            context.getSource().sendFeedback(() -> out, false);
            if (player != null) {
                context.getSource().getServer().sendMessage(TextUtils.formattable(player.getNameForScoreboard() + ": ").append(out));
            }
            ServerUtil.refreshCommandTree(context.getSource().getServer());
            return 1;
        }
        if (player != null) {
            context.getSource().sendFeedback(() -> TextUtils.formattable("Couldn't register alias \""
                    + alias + "\" as such a command or alias already exists "), false);
        }
        return 0;
    }

    /**
     * Lists all current aliases as suggestions.
     */
    private static CompletableFuture<Suggestions> listAliases(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(AliasManager.ALIASES.keySet(), builder);
    }

    /**
     * Removes an alias, if it exists.
     */
    private static int remove(CommandContext<ServerCommandSource> context) {
        String alias = context.getArgument("alias", String.class);
        ServerPlayerEntity player = context.getSource().getPlayer();
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        if (cmd != null) {
            if (cmd.global) {
                context.getSource().sendError(TextUtils.formattable("Alias \"" + alias + "\" is global and " +
                        "can't be modified via commands"));
                return 0;
            }
            MutableText out = TextUtils.formattable("Removed command alias ").append(TextUtils.formattable(alias)
                    .formatted(Formatting.GREEN));
            cmd.deregister(context.getSource().getServer(), true);
            context.getSource().sendFeedback(() -> out, false);
            if (player != null) {
                context.getSource().getServer().sendMessage(TextUtils.formattable(player.getNameForScoreboard() + ": ").append(out));
            }
            return 1;
        }
        context.getSource().sendError(TextUtils.formattable("Couldn't find alias \"" + alias + "\""));
        return 0;
    }

    /**
     * Overwrites the all current aliases from storage.
     */
    private static int reload(CommandContext<ServerCommandSource> context) {
        for (AliasedCommand alias : AliasManager.ALIASES.values()) {
            alias.deregister(context.getSource().getServer(), false);
        }
        TechnicalToolbox.ALIAS_MANAGER.readAliases(context.getSource().getServer());
        for (AliasedCommand aliasedCommand : AliasManager.ALIASES.values()) {
            try {
                aliasedCommand.register(context.getSource().getDispatcher());
            }
            catch (Exception e) {
                TechnicalToolbox.error("Something went wrong with compiling alias {}", aliasedCommand.getAlias());
            }
        }
        context.getSource().sendFeedback(() -> TextUtils.formattable("Reloaded aliases from disk"), false);
        ServerUtil.refreshCommandTree(context.getSource().getServer());
        return 0;
    }

    /**
     * Saves all aliases in memory.
     */
    private static int save(CommandContext<ServerCommandSource> context) {
        TechnicalToolbox.ALIAS_MANAGER.writeAliases(context.getSource().getServer());
        context.getSource().sendFeedback(() -> TextUtils.formattable("Wrote aliases to disk"), false);
        return 1;
    }

    /**
     * Lists aliases in alphabetical order.
     */
    private static int list(CommandContext<ServerCommandSource> context) {
        MutableText text = TextUtils.formattable("Aliases:");
        for (AliasedCommand cmd : AliasManager.ALIASES.values().stream().sorted(Comparator.comparing(AliasedCommand::
                getAlias)).toList()) {
            if (!context.getSource().isExecutedByPlayer() || context.getSource().getPlayer() != null && context.getSource().getPlayer()
                    .hasPermissionLevel(cmd.getPermission())) {text.append("\n  ").append(TextUtils.formattable(cmd.getAlias())
                    .formatted(cmd.global ? Formatting.AQUA : Formatting.WHITE)).append( TextUtils.formattable(": ")
                    .formatted(Formatting.WHITE)).append(cmd.getSyntax());
            }
        }
        context.getSource().sendFeedback(() -> text, false);
        return 1;
    }

    /**
     * Forcefully compiles an alias. Mostly useful if auto-compilation is disabled.
     */
    private static int compile(CommandContext<ServerCommandSource> context) {
        String alias = context.getArgument("alias", String.class);
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        if (cmd != null) {
            if (cmd.global) {
                context.getSource().sendError(TextUtils.formattable("Alias \"" + alias + "\" is global and " +
                        "can't be modified via commands"));
                return 0;
            }
            if (cmd.refresh(context.getSource())) {
                context.getSource().sendFeedback(() -> TextUtils.formattable("Successfully compiled alias ")
                        .append(TextUtils.formattable(alias).formatted(Formatting.GREEN)), false);
                return 1;
            }
            return 0;
        }
        context.getSource().sendError(TextUtils.formattable("Couldn't find alias \"" + alias + "\""));
        return 0;
    }

    /**
     * Renames an existing alias, if not global.
     */
    private static int modifyRename(CommandContext<ServerCommandSource> context) {
        String alias = context.getArgument("alias", String.class);
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        String newName = context.getArgument("name", String.class);
        if (cmd != null) {
            if (cmd.global) {
                context.getSource().sendError(TextUtils.formattable("Alias \"" + alias + "\" is global and " +
                        "can't be modified via commands"));
                return 0;
            }
            context.getSource().sendFeedback(() -> TextUtils.formattable("Renamed to ").append(TextUtils.formattable(newName)
                    .formatted(Formatting.GREEN)), false);
            return cmd.rename(context, newName) ? 1 : 0;
        }
        context.getSource().sendError(TextUtils.formattable("Couldn't find alias \"" + alias + "\""));
        return 0;
    }

    /**
     * Adds a line to the end of an alias and compiles afterward if auto-compilation is enabled.
     */
    private static int modifyAdd(CommandContext<ServerCommandSource> context) {
        String alias = context.getArgument("alias", String.class);
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        String command = context.getArgument("line", String.class);
        if (cmd != null) {
            if (cmd.global) {
                context.getSource().sendError(TextUtils.formattable("Alias \"" + alias + "\" is global and " +
                        "can't be modified via commands"));
                return 0;
            }
            cmd.addCommand(command);
            MutableText out = TextUtils.formattable("Added line: \"").append(TextUtils.formattable(command).formatted(Formatting.YELLOW))
                    .append(TextUtils.formattable("\"\n")).append(cmd.getCommandText());
            context.getSource().sendFeedback(() -> out, false);
            if (ConfigOptions.ALIAS_MODIFY_COMPILE.val()) {
                cmd.refresh(context.getSource());
            }
            return 1;
        }
        context.getSource().sendError(TextUtils.formattable("Couldn't find alias \"" + alias + "\""));
        return 0;
    }

    /**
     * Inserts a line at a specified position in an alias afterward if auto-compilation is enabled.
     */
    private static int modifyInsert(CommandContext<ServerCommandSource> context) {
        String alias = context.getArgument("alias", String.class);
        int line = context.getArgument("line number", Integer.class);
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        String command = context.getArgument("line", String.class);
        if (cmd != null) {
            if (cmd.global) {
                context.getSource().sendError(TextUtils.formattable("Alias \"" + alias + "\" is global and " +
                        "can't be modified via commands"));
                return 0;
            }
            MutableText err = cmd.insert(command, line);
            if (err != null) {
                context.getSource().sendError(err);
                return 0;
            }
            MutableText out = TextUtils.formattable("Inserted command at line " + line + ": \"").append(TextUtils.formattable(command)
                    .formatted(Formatting.YELLOW)).append(TextUtils.formattable("\"\n")).append(cmd.getCommandText());
            context.getSource().sendFeedback(() -> out, false);
            if (ConfigOptions.ALIAS_MODIFY_COMPILE.val()) {
                cmd.refresh(context.getSource());
            }
            return 1;
        }
        context.getSource().sendError(TextUtils.formattable("Couldn't find alias \"" + alias + "\""));
        return 0;
    }

    /**
     * Lists the current line as a suggestion when modifying an alias.
     */
    private static CompletableFuture<Suggestions> modifyLineSuggestion(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        String alias = context.getArgument("alias", String.class);
        int line = context.getArgument("line number", Integer.class);
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        Collection<String> c;
        if (cmd != null && line <= cmd.getCommands().size()) {
            if (cmd.global) {
                return CommandSource.suggestMatching(Collections.emptyList(), builder);
            }
            c = Collections.singleton(cmd.getCommands().get(line - 1));
        }
        else {
            c = Collections.emptyList();
        }
        return CommandSource.suggestMatching(c, builder);
    }

    /**
     * Replaces an existing line in an alias and compiles afterward if auto-compilation is enabled.
     */
    private static int modifySet(CommandContext<ServerCommandSource> context) {
        String alias = context.getArgument("alias", String.class);
        int line = context.getArgument("line number", Integer.class);
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        if (cmd != null) {
            if (cmd.global) {
                context.getSource().sendError(TextUtils.formattable("Alias \"" + alias + "\" is global and " +
                        "can't be modified via commands"));
                return 0;
            }
            if (line >= cmd.getCommands().size()) {
                context.getSource().sendError(TextUtils.formattable("Index \"" + line + "\" is out of bounds"));
                return 0;
            }
            MutableText err;
            String command = context.getArgument("line", String.class);
            err = cmd.insert(command, line);
            if (err != null) {
                context.getSource().sendError(err);
                return 0;
            }
            err = cmd.removeCommand(line + 1);
            if (err != null) {
                context.getSource().sendError(err);
                return 0;
            }
            MutableText out = TextUtils.formattable("Set command at line " + line + ": \"").append(TextUtils.formattable(command)
                    .formatted(Formatting.YELLOW)).append(TextUtils.formattable("\"\n")).append(cmd.getCommandText());
            context.getSource().sendFeedback(() -> out, false);
            if (ConfigOptions.ALIAS_MODIFY_COMPILE.val()) {
                cmd.refresh(context.getSource());
            }
            return 1;
        }
        context.getSource().sendError(TextUtils.formattable("Couldn't find alias \"" + alias + "\""));
        return 0;
    }

    /**
     * Removes a line from an alias and compiles afterward if auto-compilation is enabled.
     */
    private static int modifyRemove(CommandContext<ServerCommandSource> context) {
        String alias = context.getArgument("alias", String.class);
        int line = context.getArgument("line number", Integer.class);
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        if (cmd != null) {
            if (cmd.global) {
                context.getSource().sendError(TextUtils.formattable("Alias \"" + alias + "\" is global and " +
                        "can't be modified via commands"));
                return 0;
            }
            if (line >= cmd.getCommands().size()) {
                context.getSource().sendError(TextUtils.formattable("Index \"" + line + "\" is out of bounds"));
                return 0;
            }
            MutableText err;
            err = cmd.removeCommand(line);
            if (err != null) {
                context.getSource().sendError(err);
                return 0;
            }
            MutableText out = TextUtils.formattable("Removed command at " +
                    "line " + line + "\n").append(cmd.getCommandText());
            context.getSource().sendFeedback(() -> out, false);
            if (ConfigOptions.ALIAS_MODIFY_COMPILE.val()) {
                cmd.refresh(context.getSource());
            }
            return 1;
        }
        context.getSource().sendError(TextUtils.formattable("Couldn't find alias \"" + alias));
        return 0;
    }

    /**
     * Sets the minimum permission level requires to execute an alias.
     */
    private static int modifyPermission(CommandContext<ServerCommandSource> context) {
        String alias = context.getArgument("alias", String.class);
        int permissionLevel = context.getArgument("permission level", Integer.class);
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        if (cmd != null) {
            if (cmd.global) {
                context.getSource().sendError(TextUtils.formattable("Alias \"" + alias + "\" is global and " +
                        "can't be modified via commands"));
                return 0;
            }
            cmd.setPermission(permissionLevel);
            context.getSource().sendFeedback(() -> TextUtils.formattable("Permission level for alias ").append(TextUtils
                            .formattable(alias).formatted(Formatting.GREEN)).append(TextUtils.formattable(" set" + " to ")
                    .append(TextUtils.formattable(String.valueOf(permissionLevel)).formatted(Formatting.YELLOW))), false);
            return 1;
        }
        context.getSource().sendError(TextUtils.formattable("Couldn't find alias \"" + alias + "\""));
        return 0;
    }

    /**
     * Sets whether an alias should execute silently (not sending feedback to the source) or not.
     */
    private static int modifySilent(CommandContext<ServerCommandSource> context) {
        String alias = context.getArgument("alias", String.class);
        boolean silent = context.getArgument("silent execution", Boolean.class);
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        if (cmd != null) {
            if (cmd.global) {
                context.getSource().sendError(TextUtils.formattable("Alias \"" + alias + "\" is global and " +
                        "can't be modified via commands"));
                return 0;
            }
            cmd.setSilent(silent);
            context.getSource().sendFeedback(() -> TextUtils.formattable("Alias ").append(
                    TextUtils.formattable(alias).formatted(Formatting.GREEN)).append(TextUtils.formattable(" set to " + (silent ?
                    "silent " : "verbose ") + "execution mode")), false);
            return 1;
        }
        context.getSource().sendError(TextUtils.formattable("Couldn't " +
                "find alias \"" + alias + "\n"));
        return 0;
    }

    /**
     * Returns the relevant info corresponding to an alias (name, global status, permission, silent, etc.)
     */
    private static int modifyInfo(CommandContext<ServerCommandSource> context) {
        String alias = context.getArgument("alias", String.class);
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        if (cmd != null) {
            MutableText out = TextUtils.formattable("Alias ").append(TextUtils.formattable(alias).formatted(cmd.global ? Formatting.AQUA
                    : Formatting.GREEN).append(TextUtils.formattable(cmd.global ? " (global):\n" : ":\n").formatted(Formatting.WHITE)));
            out.append(cmd.getCommandText());
            out.append(TextUtils.formattable("\nPermission level: ").append(TextUtils.formattable(String.valueOf(cmd.getPermission()))
                    .formatted(Formatting.GREEN)));
            if (cmd.hasArguments()) {
                out.append("\nSyntax: ").append(cmd.getVerboseSyntax());
            }
            context.getSource().sendFeedback(() -> out, false);
            return 1;
        }
        context.getSource().sendError(TextUtils.formattable("Couldn't find alias \"" + alias + "\""));
        return 0;
    }

    /**
     * Can be used to add or modify an argument.
     * @param argType argument type, must correspond to one of the fixed argument types
     * @param replace whether the new argument should replace an existing one or not
     * @param clazz type of the argument; will be used in verification and casting
     * @param args provided arguments for the argument type, if applicable
     */
    private static int modifyArgumentSet(CommandContext<ServerCommandSource> context, String argType, boolean replace, Class<?> clazz, String... args) {
        String alias = context.getArgument("alias", String.class);
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        if (cmd != null) {
            if (cmd.global) {
                context.getSource().sendError(TextUtils.formattable("Alias \"" + alias + "\" is global and " +
                        "can't be modified via commands"));
                return 0;
            }
            String name = context.getArgument("argument", String.class);
            if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                context.getSource().sendError(TextUtils.formattable("Arguments can only contain letters, numbers, and underscores, and "
                        + "must begin with a letter or underscore"));
                return 0;
            }
            String[] stringArgs = new String[args.length];
            if ("selection".equals(argType)) {
                String selectionArgs = context.getArgument("comma_separated_selection", String.class);
                if (selectionArgs.isEmpty()) {
                    context.getSource().sendError(TextUtils.formattable("Can't accept empty selection"));
                    return 0;
                }
                stringArgs = selectionArgs.split(" *, *");
            }
            else {
                for (int i = 0; i < args.length; i++) {
                    stringArgs[i] = context.getArgument(args[i], clazz).toString();
                }
            }
            return cmd.addArgument(context.getSource(), replace, name, argType, stringArgs) ? 1 : 0;
        }
        context.getSource().sendError(TextUtils.formattable("Couldn't find alias \"" + alias + "\""));
        return 0;
    }

    /**
     * Lists existing arguments for an alias.
     */
    private static CompletableFuture<Suggestions> modifyListArguments(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        String alias = context.getArgument("alias", String.class);
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        List<String> argString = new ArrayList<>();
        if (cmd != null) {
            if (cmd.global) {
                return CommandSource.suggestMatching(Collections.emptyList(), builder);
            }
            Collection<Variable.Definition> args = cmd.getArguments();
            for (Variable.Definition var : args) {
                argString.add(var.name);
            }
        }
        return CommandSource.suggestMatching(argString, builder);
    }

    /**
     * Removes an argument from an alias and recompiles if auto-compilation is enabled.
     */
    private static int modifyArgumentRemove(CommandContext<ServerCommandSource> context) {
        String alias = context.getArgument("alias", String.class);
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        if (cmd != null) {
            if (cmd.global) {
                context.getSource().sendError(TextUtils.formattable("Alias \"" + alias + "\" is global and " +
                        "can't be modified via commands"));
                return 0;
            }
            String name = context.getArgument("argument", String.class);
            return cmd.removeArgument(context.getSource(), name) ? 0 : 1;
        }
        context.getSource().sendError(TextUtils.formattable("Couldn't find alias \"" + alias + "\""));
        return 0;
    }

    /**
     * Renames an argument in an alias and recompiles if auto-compilation is enabled.
     */
    private static int modifyArgumentRename(CommandContext<ServerCommandSource> context) {
        String alias = context.getArgument("alias", String.class);
        AliasedCommand cmd = AliasManager.ALIASES.get(alias);
        if (cmd != null) {
            if (cmd.global) {
                context.getSource().sendError(TextUtils.formattable("Alias \"" + alias + "\" is global and " +
                        "can't be modified via commands"));
                return 0;
            }
            String argument = context.getArgument("argument", String.class);
            String name = context.getArgument("newArgument", String.class);
            return cmd.renameArgument(context.getSource(), argument, name) ? 0 : 1;
        }
        context.getSource().sendError(TextUtils.formattable("Couldn't find alias \"" + alias + "\""));
        return 0;
    }

    /**
     * Build the command tree for adding/setting arguments using literals.
     */
    private static LiteralArgumentBuilder<ServerCommandSource> addOrSetArguments(String literalName, boolean replace) {
        return CommandManager.literal(literalName)
                .then(CommandManager.argument("argument", StringArgumentType.word())
                        .suggests((context, builder) -> replace ? AliasCommand.modifyListArguments(context, builder) : Suggestions.empty())
                        .then(CommandManager.literal("int")
                                .executes(context -> modifyArgumentSet(context, "int", replace, Integer.class)))
                        .then(CommandManager.literal("int_range")
                                .then(CommandManager.argument("min", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("max", IntegerArgumentType.integer())
                                                .executes(context -> modifyArgumentSet(context, "int_range", replace, Integer.class,
                                                        "min", "max")))))
                        .then(CommandManager.literal("long")
                                .executes(context -> modifyArgumentSet(context, "long", replace, Long.class)))
                        .then(CommandManager.literal("long_range")
                                .then(CommandManager.argument("min", LongArgumentType.longArg())
                                        .then(CommandManager.argument("max", LongArgumentType.longArg())
                                                .executes(context -> modifyArgumentSet(context, "long_range", replace, Long.class,
                                                        "min", "max")))))
                        .then(CommandManager.literal("float")
                                .executes(context -> modifyArgumentSet(context, "float", replace, Float.class)))
                        .then(CommandManager.literal("float_range")
                                .then(CommandManager.argument("min", FloatArgumentType.floatArg())
                                        .then(CommandManager.argument("max", FloatArgumentType.floatArg())
                                                .executes(context -> modifyArgumentSet(context, "float_range", replace, Float.class,
                                                        "min", "max")))))
                        .then(CommandManager.literal("double")
                                .executes(context -> modifyArgumentSet(context, "double", replace, Double.class)))
                        .then(CommandManager.literal("double_range")
                                .then(CommandManager.argument("min", DoubleArgumentType.doubleArg())
                                        .then(CommandManager.argument("max", DoubleArgumentType.doubleArg())
                                                .executes(context -> modifyArgumentSet(context, "double_range", replace,
                                                        Double.class, "min", "max")))))
                        .then(CommandManager.literal("boolean")
                                .executes(context -> modifyArgumentSet(context, "boolean", replace, Boolean.class)))
                        .then(CommandManager.literal("word")
                                .executes(context -> modifyArgumentSet(context, "word", replace, String.class)))
                        .then(CommandManager.literal("string")
                                .executes(context -> modifyArgumentSet(context, "string", replace, String.class)))
                        .then(CommandManager.literal("regex")
                                .then(CommandManager.argument("regex", StringArgumentType.string())
                                        .executes(context -> modifyArgumentSet(context, "regex", replace, String.class,
                                                "regex"))))
                        .then(CommandManager.literal("selection")
                                .then(CommandManager.argument("comma_separated_selection", StringArgumentType.greedyString())
                                        .executes(context -> modifyArgumentSet(context, "selection", replace, String.class)))));
    }

}
