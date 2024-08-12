package com.birblett.impl.command.alias;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.config.ConfigOption;
import com.birblett.lib.command.CommandSourceModifier;
import com.birblett.util.ServerUtil;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data container for aliased commands/command scripts
 */
public class AliasedCommand {

    public record Entry<T>(int argc, Function<String[], ArgumentType<T>> argumentTypeProvider, Class<T> clazz) {}

    public static class VariableDefinition {

        public final String name;
        public final String typeName;
        public final Entry<?> type;
        public final String[] args;

        public VariableDefinition(String name, String type, String[] args) {
            this.name = name;
            this.typeName = type;
            this.type = ARGUMENT_TYPES.getOrDefault(type, ARGUMENT_TYPES.get("string"));
            this.args = args;
        }

        public ArgumentType<?> getArgumentType() {
            return this.type.argumentTypeProvider().apply(this.args);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

    }

    public static final HashMap<String, Entry<?>> ARGUMENT_TYPES = new HashMap<>();
    static {
        ARGUMENT_TYPES.put("int", new Entry<>(0, opt -> IntegerArgumentType.integer(), Integer.class));
        ARGUMENT_TYPES.put("int_range", new Entry<>(2, opt -> AliasedCommand.rangeArgumentType(opt, Integer.class, Integer::parseInt,
                IntegerArgumentType::integer), Integer.class));
        ARGUMENT_TYPES.put("long", new Entry<>(0, opt -> LongArgumentType.longArg(), Long.class));
        ARGUMENT_TYPES.put("long_range", new Entry<>(2, opt -> AliasedCommand.rangeArgumentType(opt, Long.class, Long::parseLong,
                LongArgumentType::longArg), Long.class));
        ARGUMENT_TYPES.put("float", new Entry<>(0, opt -> FloatArgumentType.floatArg(), Float.class));
        ARGUMENT_TYPES.put("float_range", new Entry<>(2, opt -> AliasedCommand.rangeArgumentType(opt, Float.class, Float::parseFloat,
                FloatArgumentType::floatArg), Float.class));
        ARGUMENT_TYPES.put("double", new Entry<>(0, opt -> DoubleArgumentType.doubleArg(), Double.class));
        ARGUMENT_TYPES.put("double_range", new Entry<>(2, opt -> AliasedCommand.rangeArgumentType(opt, Double.class,
                Double::parseDouble, DoubleArgumentType::doubleArg), Double.class));
        ARGUMENT_TYPES.put("boolean", new Entry<>(0, opt -> BoolArgumentType.bool(), Boolean.class));
        ARGUMENT_TYPES.put("word", new Entry<>(0, opt -> StringArgumentType.word(), String.class));
        ARGUMENT_TYPES.put("string", new Entry<>(0, opt -> StringArgumentType.string(), String.class));
        ARGUMENT_TYPES.put("regex", new Entry<>(1, opt -> StringArgumentType.string(), String.class));
        ARGUMENT_TYPES.put("selection", new Entry<>(-1, opt -> StringArgumentType.string(), String.class));
    }



    private final String alias;
    private final List<String> commands = new ArrayList<>();
    private final LinkedHashMap<Integer, String> instructions = new LinkedHashMap<>();
    private final LinkedHashMap<String, VariableDefinition> argumentDefinitions = new LinkedHashMap<>();
    private final HashMap<String, String> arguments = new HashMap<>();
    private int permission;
    private boolean silent;
    private static final Pattern SAVED_ARGS = Pattern.compile("\\{\\$[^:]+(:[^}]+)?}");
    private static final Pattern VAR_ACCESS_REGEX = Pattern.compile("\\{\\$[^}]+}");

    public AliasedCommand(String alias, String command, CommandDispatcher<ServerCommandSource> dispatcher) {
        this.alias = alias;
        this.commands.add(command);
        this.permission = ConfigOption.ALIAS_DEFAULT_PERMISSION.val();
        this.silent = ConfigOption.ALIAS_DEFAULT_SILENT.val();
        this.register(dispatcher);
    }

    private AliasedCommand(String alias, int permission, boolean silent, CommandDispatcher<ServerCommandSource> dispatcher, Collection<String> commands, Collection<VariableDefinition> arguments) {
        this.alias = alias;
        this.commands.addAll(commands);
        for (VariableDefinition var : arguments) {
            this.argumentDefinitions.put(var.name, var);
        }
        this.permission = permission;
        this.silent = silent;
        this.register(dispatcher);
    }

    public String getAlias() {
        return this.alias;
    }

    public void setPermission(int permission) {
        this.permission = permission;
    }

    public int getPermission() {
        return this.permission;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    public List<String> getCommands() {
        return this.commands;
    }

    private boolean compile() {
        this.instructions.clear();
        for (String s : this.commands) {
            if (!s.isEmpty()) {
                int instructionType = 0;
                String cmd = s.strip();
                this.instructions.put(instructionType, cmd);
                Matcher m = VAR_ACCESS_REGEX.matcher(cmd);
                while (m.find()) {
                    String b = m.group();
                }
            }
        }
        return true;
    }

    public boolean register(CommandDispatcher<ServerCommandSource> dispatcher) {
        ArgumentBuilder<ServerCommandSource, ?> tree = null;
        // Execution with required arguments
        if (!this.argumentDefinitions.isEmpty()) {
            if (!this.compile()) {
                return false;
            }
            // disgusting hack to build the command tree from the bottom up. thanks for the tip mojang
            VariableDefinition[] vars = this.argumentDefinitions.values().toArray(new VariableDefinition[0]);
            for (int i = vars.length - 1; i >= 0; i--) {
                VariableDefinition def = vars[i];
                // arguments processed here
                if (tree == null) {
                    RequiredArgumentBuilder<ServerCommandSource, ?> base = CommandManager.argument(def.name, def.getArgumentType());
                    if ("selection".equals(def.typeName)) {
                        base = base.suggests(((context, builder) -> CommandSource.suggestMatching(def.args, builder)));
                    }
                    tree = base.executes(context -> {
                        this.arguments.clear();
                        for (VariableDefinition var : this.argumentDefinitions.values()) {
                            this.arguments.put(var.typeName, context.getArgument(var.name, var.type.clazz()).toString());
                        }
                        return this.execute(context);
                    });
                }
                else {
                    tree = CommandManager.argument(def.name, def.getArgumentType()).then(tree);
                }
            }
            dispatcher.register((CommandManager.literal(this.alias)
                    .requires(source -> source.hasPermissionLevel(this.getPermission())))
                    .then(tree)
                    .executes(this::getCommandInfo));
        }
        // Execution if no argc provided
        else {
            dispatcher.register((CommandManager.literal(this.alias)
                    .requires(source -> source.hasPermissionLevel(this.getPermission())))
                    .executes(this::execute));
        }
        if (!AliasManager.ALIASES.containsKey(this.alias)) {
            AliasManager.ALIASES.put(this.alias, this);
        }
        return true;
    }

    /**
     * Adds a command to the end of the current alias script and automatically updates argument count.
     * @param command full command to add.
     */
    public void addCommand(String command, MinecraftServer server) {
        this.commands.add(command);
        this.deregister(server);
        this.register(server.getCommandManager().getDispatcher());
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
    public MutableText removeCommand(int line, MinecraftServer server) {
        if (this.commands.size() <= 1) {
            return TextUtils.formattable("Can't remove the last line in an alias");
        }
        if (line < 1 || line - 1 > this.commands.size()) {
            return TextUtils.formattable("Line index " + line + " out of bounds");
        }
        this.commands.remove(line - 1);
        this.deregister(server);
        this.register(server.getCommandManager().getDispatcher());
        return null;
    }

    /**
     * Inserts a line at a position, moving lines below down
     * @param line command string to be inserted
     * @param num line number to insert at (or before)
     * @return an error message if unsuccessful
     */
    public MutableText insert(String line, int num, MinecraftServer server) {
        if (num < 1 || num - 1 > this.commands.size()) {
            return TextUtils.formattable("Line index " + num + " out of bounds");
        }
        this.commands.add(num - 1, line);
        this.deregister(server);
        this.register(server.getCommandManager().getDispatcher());
        return null;
    }

    /**
     * @return example of command usage
     */
    public MutableText getSyntax() {
        MutableText out = TextUtils.formattable("/").formatted(Formatting.YELLOW);
        out.append(TextUtils.formattable(this.alias).formatted(Formatting.YELLOW)).append(" ");
        for (VariableDefinition arg : this.argumentDefinitions.values()) {
            out.append(TextUtils.formattable("<" + arg.name + "> ").formatted(Formatting.GREEN));
        }
        return out;
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
     * Executes a command.
     * @param context command context
     */
    private int execute(CommandContext<ServerCommandSource> context) {
        for (String command : this.instructions.values()) {
            for (VariableDefinition var : this.argumentDefinitions.values()) {
                command = command.replaceAll("\\{\\$" + var.name + "}", this.arguments.get(var.name));
            }
            if (!this.executeCommand(context, command)) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Generic number range argument type
     */
    @SuppressWarnings("unchecked")
    private static <T extends Number> ArgumentType<T> rangeArgumentType(String[] opt, Class<T> clazz, Function<String, T> parse, BiFunction<T, T, ArgumentType<T>> argumentType) {
        T min, max;
        try {
            min = parse.apply(opt[0]);
            max = parse.apply(opt[1]);
            if (((Comparable<T>) min).compareTo(max) > 0) {
                throw new Exception();
            }
        }
        catch (Exception e) {
            try {
                min = (T) clazz.getDeclaredField("MIN_VALUE").get(null);
                max = (T) clazz.getDeclaredField("MAX_VALUE").get(null);
            }
            catch (Exception e2) {
                min = (T) Integer.valueOf(0);
                max = (T) Integer.valueOf(0);
            }
        }
        return argumentType.apply(min, max);
    }

    /**
     * Adds an argument, failing if it already exists.
     * @param source command source to send feedback to
     * @param replace whether the argument should replace the old one
     * @param name name of the argument
     * @param argType argument type
     * @param args optional args
     * @return false if failed to add argument, true otherwise
     */
    public boolean addArgument(ServerCommandSource source, boolean replace, String name, String argType, String[] args) {
        if (this.argumentDefinitions.containsKey(name)) {
            if (!replace) {
                source.sendError(TextUtils.formattable("Argument \"" + name + "\"already exists"));
                return false;
            }
        }
        if (!ARGUMENT_TYPES.containsKey(argType)) {
            source.sendError(TextUtils.formattable("Not a valid argument type: " + argType));
            return false;
        }
        this.argumentDefinitions.put(name, new VariableDefinition(name, argType, args));
        source.sendFeedback(() -> {
            MutableText out = TextUtils.formattable((replace ? "Set" : "Added") + " argument ").append(TextUtils.formattable(name)
                    .formatted(Formatting.GREEN)).append(TextUtils.formattable(" of type ").formatted(Formatting.WHITE))
                    .append(TextUtils.formattable(argType).formatted(Formatting.GREEN));
            if (args.length > 0) {
                out.append(TextUtils.formattable(" with args [").formatted(Formatting.WHITE));
                for (int i = 0; i < args.length; i++) {
                    out.append(TextUtils.formattable(args[i]).formatted(Formatting.YELLOW));
                    if (i < args.length - 1) {
                        out.append(TextUtils.formattable(",").formatted(Formatting.WHITE));
                    }
                }
                out.append(TextUtils.formattable("]").formatted(Formatting.WHITE));
            }
            return out;
        }, false);
        return true;
    }

    /**
     * Removes an argument with the specified name
     * @param source source to send feedback to
     * @param name name of argument
     * @return true if successful, false if not
     */
    public boolean removeArgument(ServerCommandSource source, String name) {
        if (this.argumentDefinitions.containsKey(name)) {
            this.argumentDefinitions.remove(name);
            source.sendFeedback(() -> TextUtils.formattable("Removed argument ").append(TextUtils.formattable(name)
                    .formatted(Formatting.GREEN)), false);
            return true;
        }
        source.sendError(TextUtils.formattable("Argument \"" + name + "\" not found"));
        return false;
    }

    public Collection<VariableDefinition> getArguments() {
        return this.argumentDefinitions.values();
    }

    public boolean hasArguments() {
        return !this.argumentDefinitions.isEmpty();
    }

    /**
     * Display command syntax to the executor
     * @param context contains the executor to send feedback to
     */
    private int getCommandInfo(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(this::getSyntax, false);
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
            if (this.silent != (ConfigOption.ALIAS_DEFAULT_SILENT.val())) {
                bufferedWriter.write("Silent: \"" + this.silent + "\"\n");
            }
            if (!this.argumentDefinitions.isEmpty()) {
                bufferedWriter.write("Arguments:");
                for (VariableDefinition var : this.argumentDefinitions.values()) {
                    bufferedWriter.write(" {$" + var.name + ":" + var.typeName);
                    if (var.args.length > 0) {
                        bufferedWriter.write("|");
                        for (int i = 0; i < var.args.length; i++) {
                            bufferedWriter.write(var.args[i] + ((i < var.args.length - 1) ? "," : ""));
                        }
                    }
                    bufferedWriter.write("}");
                }
                bufferedWriter.write("\n");
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
            String line, alias = null;
            int permission = ConfigOption.ALIAS_DEFAULT_PERMISSION.val();
            List<String> commands = new ArrayList<>();
            List<VariableDefinition> arguments = new ArrayList<>();
            while ((line = bufferedReader.readLine()) != null) {
                if (!readingCommandState) {
                    String[] split = line.split(":");
                    if (split.length >= 2) {
                        switch (split[0].toLowerCase()) {
                            case "alias" -> alias = line.replaceFirst("(?i)Alias: *", "").strip();
                            case "permission level" -> {
                                String tmp = line.replaceFirst("(?i)Permission level: *", "").strip();
                                try {
                                    permission = Integer.parseInt(tmp);
                                } catch (NumberFormatException e) {
                                    TechnicalToolbox.log(path + ": Couldn't parse \"" + tmp + "\" as int");
                                    return false;
                                }
                            }
                            case "silent" -> {
                                String tmp = line.replaceFirst("(?i)Silent: *", "").strip();
                                silent = Boolean.parseBoolean(tmp);
                            }
                            case "arguments" -> {
                                String tmp = line.replaceFirst("(?i)Arguments: *", "").strip();
                                Matcher m = SAVED_ARGS.matcher(tmp);
                                while (m.find()) {
                                    String arg = m.group().replaceFirst("\\{\\$", "");
                                    arg = arg.substring(0, arg.length() - 1);
                                    String[] temp = arg.split(":");
                                    String name = temp[0];
                                    if (temp.length == 1) {
                                        arguments.add(new VariableDefinition(name, "string", new String[0]));
                                    }
                                    else {
                                        temp = temp[1].split("\\|");
                                        arguments.add(new VariableDefinition(name, temp[0], temp.length > 1 ? temp[1].split(",") :
                                                new String[0]));
                                    }
                                }
                            }
                        }
                    }
                    else if (split.length == 1 && split[0].equalsIgnoreCase("command list")) {
                        readingCommandState = true;
                    }
                }
                else {
                    if (!line.isEmpty()) {
                        commands.add(line.stripTrailing());
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
            new AliasedCommand(alias, permission, silent, server.getCommandManager().getDispatcher(), commands, arguments);
        }
        catch (IOException e) {
            TechnicalToolbox.warn("Something went wrong reading from file " + path);
        }
        return true;
    }

}
