package com.birblett.impl.alias;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.config.ConfigOption;
import com.birblett.lib.command.CommandSourceModifier;
import com.birblett.util.ServerUtil;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data container for aliased commands/command scripts
 */
public class AliasedCommand {

    private final String alias;
    private final List<String> commands = new ArrayList<>();
    private final Set<String> args = new LinkedHashSet<>();
    private int argCount;
    private int permission;
    private boolean silent;
    private String separator;
    private static final Pattern ARG = Pattern.compile("\\{\\$[^{$}]+}");
    private static final String[] VALID = new String[]{"int", "float", "double", "boolean", "word", "letters",
            "alphanumeric", "equals", "ci_equals", "regex"};

    public AliasedCommand(String alias, String command, CommandDispatcher<ServerCommandSource> dispatcher) {
        this.alias = alias;
        this.commands.add(command);
        this.permission = ConfigOption.ALIAS_DEFAULT_PERMISSION.val();
        this.separator = ConfigOption.ALIAS_DEFAULT_SEPARATOR.val();
        this.silent = ConfigOption.ALIAS_DEFAULT_SILENT.val();
        this.updateArgCount();
        this.register(dispatcher);
    }

    private AliasedCommand(String alias, int permission, String separator, boolean silent, CommandDispatcher<ServerCommandSource> dispatcher, Collection<String> commands) {
        this.alias = alias;
        this.commands.addAll(commands);
        this.permission = permission;
        this.separator = separator;
        this.silent = silent;
        this.updateArgCount();
        this.register(dispatcher);
    }

    public String getAlias() {
        return this.alias;
    }

    public int getArgCount() {
        return this.argCount;
    }

    public void setPermission(int permission) {
        this.permission = permission;
    }

    public int getPermission() {
        return this.permission;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    public String getSeparator() {
        return this.separator;
    }

    public List<String> getCommands() {
        return this.commands;
    }

    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        if (!AliasManager.ALIASES.containsKey(this.alias)) {
            AliasManager.ALIASES.put(this.alias, this);
        }
        ArgumentBuilder<ServerCommandSource, ?> tree = null;
        // disgusting hack to build the command tree from the bottom up. thanks for the tip mojang
        if (this.argCount > 0) {
            String[] str = this.args.toArray(new String[0]);
            for (int i = str.length - 1; i >= 0; i--) {
                if (tree == null) {
                    tree = CommandManager.argument(str[i], StringArgumentType.string()).executes(context -> {
                        String[] args = new String[str.length];
                        for (int j = 0; j < str.length; j++) {
                            args[j] = context.getArgument(str[j], String.class);
                        }
                        return this.execute(context, args);
                    });
                }
                else {
                    tree = CommandManager.argument(str[i], StringArgumentType.string()).then(tree);
                }
            }
        }
        // Execution with required arguments
        if (this.argCount > 0) {
            dispatcher.register((CommandManager.literal(this.alias)
                    .requires(source -> source.hasPermissionLevel(this.getPermission())))
                    .then(tree));
        }
        // Execution if no args provided
        else {
            dispatcher.register((CommandManager.literal(this.alias)
                    .requires(source -> source.hasPermissionLevel(this.getPermission())))
                    .executes(this::execute));
        }
    }

    /**
     * Updates the current argument count based on all commands in the alias.
     */
    private void updateArgCount() {
        this.args.clear();
        this.argCount = 0;
        for (String command : this.commands) {
            Matcher m = ARG.matcher(command);
            while (m.find()) {
                String s = m.group();
                this.argCount++;
                this.args.add(s.substring(2, s.length() - 1));
            }
        }
    }

    /**
     * Adds a command to the end of the current alias script and automatically updates argument count.
     * @param command full command to add.
     */
    public void addCommand(String command) {
        this.commands.add(command);
        this.updateArgCount();
    }

    /**
     * @return full command script as text.
     */
    public MutableText getCommandText() {
        MutableText out = TextUtils.formattable("Commands:\n");
        int lineNum = 0;
        for (String line : this.getCommands()) {
            out.append(TextUtils.formattable( " " + ++lineNum + ". ")).append(TextUtils.formattable(line)
                    .formatted(Formatting.YELLOW));
            if (lineNum != this.getCommands().size()) {
                out.append(TextUtils.formattable("\n"));
            }
        }
        return out;
    }

    /**
     * Removes the command at the given position. Can't remove the last line of an alias.
     * @param line line number
     * @return Fail message if removal failed, otherwise null.
     */
    public MutableText removeCommand(int line) {
        if (this.commands.size() <= 1) {
            return TextUtils.formattable("Can't remove the last line in an alias");
        }
        if (line < 1 || line - 1 > this.commands.size()) {
            return TextUtils.formattable("Line index " + line + " out of bounds");
        }
        this.commands.remove(line - 1);
        this.updateArgCount();
        return null;
    }

    /**
     * Inserts a command at a position, moving lines below down
     * @param command command string to be inserted
     * @param line line to insert at (or before)
     * @return an error message if unsuccessful
     */
    public MutableText insertCommand(String command, int line) {
        if (line < 1 || line - 1 > this.commands.size()) {
            return TextUtils.formattable("Line index " + line + " out of bounds");
        }
        this.commands.add(line - 1, command);
        this.updateArgCount();
        return null;
    }

    /**
     * @return example of command usage
     */
    public MutableText getSyntax() {
        MutableText out = TextUtils.formattable("/").formatted(Formatting.YELLOW);
        out.append(TextUtils.formattable(this.alias).formatted(Formatting.YELLOW)).append(" ");
        int i = 0;
        for (String arg : this.args) {
            out.append(TextUtils.formattable(arg).formatted(Formatting.GREEN));
            ++i;
            if (i < this.args.size()) {
                out.append(TextUtils.formattable(separator).formatted(Formatting.YELLOW));
            }
        }
        return out;
    }

    /**
     * Comma separated required arguments.
     * @return MutableText with args (in yellow) separated by commas (in white)
     */
    private MutableText getCommaSeparateArgs() {
        MutableText text = TextUtils.formattable("");
        int i = 0;
        for (String command : this.args) {
            text.append(TextUtils.formattable(command).formatted(Formatting.YELLOW));
            if (++i < this.args.size()) {
                text.append(TextUtils.formattable(", "));
            }
        }
        return text;
    }

    /**
     * Deregisters a command alias and resends the command tree.
     * @param server server to deregister commands from.
     */
    public void deregister(MinecraftServer server) {
        ServerUtil.removeCommandByName(server, this.alias);
        AliasManager.ALIASES.remove(this.alias);
    }

    /**
     * Checks if command string is a validator
     * @param command input command to check
     * @return validation type if command string is a validator, else null
     */
    private String checkValidation(String command) {
        String validationType = null;
        for (String validation : AliasedCommand.VALID) {
            if (command.startsWith(validation + "(")) {
                if (!command.endsWith(")")) {
                    return "invalid";
                }
                validationType = validation;
            }
        }
        return validationType;
    }

    /**
     * Executes command on server with command permission level override enabled
     * @param context command context
     * @param command command to execute
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean executeCommand(CommandContext<ServerCommandSource> context, String command) {
        ServerCommandSource source = context.getSource();
        CommandDispatcher<ServerCommandSource> dispatcher = source.getServer().getCommandManager().getDispatcher();
        ((CommandSourceModifier) source).technicalToolbox$setPermissionOverride(true);
        if (this.silent) {
            ((CommandSourceModifier) source).technicalToolbox$shutUp(true);
        }
        try {
            dispatcher.execute(dispatcher.parse(command, source));
            ((CommandSourceModifier) source).technicalToolbox$shutUp(false);
        }
        catch (CommandSyntaxException e) {
            context.getSource().sendError(TextUtils.formattable(e.getMessage()));
            ((CommandSourceModifier) source).technicalToolbox$shutUp(false);
            return false;
        }
        ((CommandSourceModifier) source).technicalToolbox$setPermissionOverride(false);
        return true;
    }

    /**
     * Attempts to parse input as a number
     * @param argName argument name
     * @param arg passed argument value
     * @param parse parse function
     * @param type argument type
     * @param context command context; will send error messages here
     * @return if number was successfully parsed
     */
    private boolean parseNumber(String argName, String arg, Function<String, ? extends Number> parse, String type, CommandContext<ServerCommandSource> context) {
        try {
            parse.apply(arg);
            return true;
        }
        catch (NumberFormatException e) {
            this.validationError(context, argName, arg, "not a valid " + type);
            return false;
        }
    }

    /**
     * Performs validation on a specific command argument, with various types of validators accepted<br/>
     * dude this is unreadable oh my god this needs a refactor
     * @param context command context
     * @param type see {@link AliasedCommand#VALID} for accepted validation types
     * @param command alias command string
     * @param args provided command args
     * @return false if invalid or not a valid type, otherwise true
     */
    private boolean validate(CommandContext<ServerCommandSource> context, @NotNull String type, String command, String[] args) {
        String arg = command.substring(0, command.length() - 1).replace(type + "(", "");
        String[] argList = arg.split(" *" + this.separator + " *");
        if (!this.args.contains(argList[0])) {
            context.getSource().sendError(TextUtils.formattable("No argument \"" + arg + "\" for alias " + this.alias));
            return false;
        }
        int i = 0;
        for (String tmp : this.args) {
            if (argList[0].equals(tmp)) {
                break;
            }
            ++i;
        }
        switch (type) {
            case "int" -> {
                if (!this.exactArgumentCount(context, argList, type, 1)) {
                    return false;
                }
                return this.parseNumber(arg, args[i], Integer::parseInt, type, context);
            }
            case "float" -> {
                if (!this.exactArgumentCount(context, argList, type, 1)) {
                    return false;
                }
                return this.parseNumber(arg, args[i], Float::parseFloat, type, context);
            }
            case "double" -> {
                if (!this.exactArgumentCount(context, argList, type, 1)) {
                    return false;
                }
                return this.parseNumber(arg, args[i], Double::parseDouble, type, context);
            }
            case "boolean" -> {
                if (!this.exactArgumentCount(context, argList, type, 1)) {
                    return false;
                }
                if (!(args[i].equalsIgnoreCase("true") || args[i].equalsIgnoreCase("false"))) {
                    this.validationError(context, arg, args[i], "not a valid boolean");
                    return false;
                }
            }
            case "word" -> {
                if (!this.exactArgumentCount(context, argList, type, 1)) {
                    return false;
                }
                if (args[i].contains(" ")) {
                    this.validationError(context, arg, args[i], "not a valid word");
                    return false;
                }
            }
            case "letters" -> {
                if (!this.exactArgumentCount(context, argList, type, 1)) {
                    return false;
                }
                for (char c : args[i].toCharArray()) {
                    if(!Character.isLetter(c)) {
                        this.validationError(context, arg, args[i], "must only contain letters");
                        return false;
                    }
                }
            }
            case "alphanumeric" -> {
                if (!this.exactArgumentCount(context, argList, type, 1)) {
                    return false;
                }
                if (!StringUtils.isAlphanumeric(args[i])) {
                    this.validationError(context, arg, args[i], "not alphanumeric");
                    return false;
                }
            }
            case "equals", "ci_equals" -> {
                boolean isIn = false;
                StringBuilder validArgs = new StringBuilder();
                if (!this.multiArgumentCount(context, argList, type, 2)) {
                    return false;
                }
                boolean caseInsensitive = type.equals("ci_equals");
                for (int j = 1; j < argList.length; j++) {
                    String cmp1 = caseInsensitive ? args[i].toLowerCase() : args[i];
                    String cmp2 = caseInsensitive ? argList[j].toLowerCase() : argList[j];
                    validArgs.append(cmp2);
                    if (j < argList.length - 1) {
                        validArgs.append(", ");
                    }
                    if (cmp1.equals(cmp2)) {
                        isIn = true;
                    }
                }
                if (!isIn) {
                    this.validationError(context, argList[0], args[i], "should be one of [" + validArgs + "]" +
                            (caseInsensitive ? " (case insensitive)" : ""));
                    return false;
                }
            }
            case "regex" -> {
                if (!this.exactArgumentCount(context, argList, type, 2)) {
                    return false;
                }
                String re = argList[1];
                int len = re.length();
                if (re.charAt(0) == '"' && re.charAt(len - 1) == '"') {
                    re = re.substring(1, len - 1);
                }
                if (!args[i].matches(re)) {
                    this.validationError(context, argList[0], args[i], "does not match regex \"" + argList[1] + "\"");
                    return false;
                }
            }
            default -> {
                context.getSource().sendError(TextUtils.formattable("Something went wrong parsing validation command"));
                return false;
            }
        }
        return true;
    }

    private boolean exactArgumentCount(CommandContext<ServerCommandSource> context, String[] argList, String validator, int count) {
        if (argList.length != count) {
            context.getSource().sendError(TextUtils.formattable("Syntax error: \"" + validator + "\" validator " +
                    "requires " + count + " argument" + (count > 1 ? "s" : "")));
            return false;
        }
        return true;
    }

    private boolean multiArgumentCount(CommandContext<ServerCommandSource> context, String[] argList, String validator, int atLeast) {
        if (argList.length < atLeast) {
            context.getSource().sendError(TextUtils.formattable("Syntax error: \"" + validator + "\" validator " +
                    "requires at least " + atLeast + " arguments"));
            return false;
        }
        return true;
    }

    private void validationError(CommandContext<ServerCommandSource> context, String name, String arg, String isNot) {
        context.getSource().sendError(TextUtils.formattable(name + ": \"" + arg + "\" " + isNot));
    }

    /**
     * Execute commands with args.
     * @param context command context
     * @param args input args
     */
    public int execute(CommandContext<ServerCommandSource> context, String[] args) {
        for (String command : this.commands) {
            String validation = this.checkValidation(command);
            if (validation == null) {
                int i = 0;
                for (String arg : this.args) {
                    command = command.replaceAll("\\{\\$" + arg + "}", args[i]);
                    i++;
                }
                if (!this.executeCommand(context, command)) {
                    return 1;
                }
            }
            else if (!this.validate(context, validation, command, args)) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Executes a command without any args.
     * @param context command context
     */
    public int execute(CommandContext<ServerCommandSource> context) {
        for (String command : this.commands) {
            if (!this.executeCommand(context, command)) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Writes alias, permission level, separator (if applicable)
     * @param path filepath to write to
     * @return whether alias was written successfully or not
     */
    public boolean writeToFile(Path path) {
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path)) {
            bufferedWriter.write("Alias: " + this.alias + "\n");
            if (this.permission != ConfigOption.ALIAS_DEFAULT_PERMISSION.val()) {
                bufferedWriter.write("Permission level: " + this.permission + "\n");
            }
            if (!this.separator.equals(ConfigOption.ALIAS_DEFAULT_SEPARATOR.getWriteable())) {
                bufferedWriter.write("Argument separator: \"" + this.separator + "\"\n");
            }
            if (this.silent != (ConfigOption.ALIAS_DEFAULT_SILENT.val())) {
                bufferedWriter.write("Silent: \"" + this.silent + "\"\n");
            }
            bufferedWriter.write("Command list:\n");
            for (String command : this.commands) {
                bufferedWriter.write(command + "\n");
            }
        } catch (IOException e) {
            TechnicalToolbox.warn("Something went wrong writing to file " + path);
            return false;
        }
        return true;
    }

    /**
     * Recreates an alias from an alias file. Alias, separator, and permlevel can come in any order and will use defaults
     * if not provided, but commands must come last.
     * @param server minecraft server, used to get dispatcher
     * @param path path to read from
     * @return whether alias was successfully restored or not; outputs errors if failed
     */
    public static boolean readFromFile(MinecraftServer server, Path path) {
        try (BufferedReader bufferedReader = Files.newBufferedReader(path)) {
            boolean readingCommandState = false, silent = ConfigOption.ALIAS_DEFAULT_SILENT.val();
            String line, alias = null, separator = ConfigOption.ALIAS_DEFAULT_SEPARATOR.getWriteable();
            int permission = ConfigOption.ALIAS_DEFAULT_PERMISSION.val();
            List<String> commands = new ArrayList<>();
            while ((line = bufferedReader.readLine()) != null) {
                if (!readingCommandState) {
                    String[] split = line.split(":");
                    if (split.length >= 2) {
                        switch (split[0].toLowerCase()) {
                            case "alias" -> alias = line.replaceFirst("(?i)Alias: *", "");
                            case "argument separator" -> {
                                separator = line.replaceFirst("(?i)Argument separator: *", "");
                                separator = separator.substring(1, separator.length() - 1);
                            }
                            case "permission level" -> {
                                String tmp = line.replaceFirst("(?i)Permission level: *", "");
                                try {
                                    permission = Integer.parseInt(tmp);
                                } catch (NumberFormatException e) {
                                    TechnicalToolbox.log(path + ": Couldn't parse \"" + tmp + "\" as int");
                                    return false;
                                }
                            }
                            case "silent" -> {
                                String tmp = line.replaceFirst("(?i)Silent: *", "");
                                silent = Boolean.parseBoolean(tmp);
                            }
                        }
                    }
                    else if (split.length == 1 && split[0].equalsIgnoreCase("command list")) {
                        readingCommandState = true;
                    }
                }
                else {
                    if (!line.isEmpty()) {
                        commands.add(line.strip());
                    }
                }
            }
            if (alias == null) {
                TechnicalToolbox.log(path + ": Alias not specified in file");
                return false;
            }
            if (commands.isEmpty()) {
                TechnicalToolbox.log(path + ": Missing script body");
                return false;
            }
            new AliasedCommand(alias, permission, separator, silent, server.getCommandManager().getDispatcher(), commands);
        }
        catch (IOException e) {
            TechnicalToolbox.warn("Something went wrong reading from file " + path);
        }
        return true;
    }

}
