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

    private record Entry<T>(int args, Function<String, ArgumentType<T>> argumentTypeProvider, Class<T> clazz) {}

    private static final HashMap<String, Entry<?>> VALIDATION_MAP = new HashMap<>();
    static {
        VALIDATION_MAP.put("int", new Entry<>(0, opt -> IntegerArgumentType.integer(), Integer.class));
        VALIDATION_MAP.put("int_range", new Entry<>(2, opt -> AliasedCommand.rangeArgumentType(opt, Integer.class, Integer::parseInt,
                IntegerArgumentType::integer), Integer.class));
        VALIDATION_MAP.put("long", new Entry<>(0, opt -> LongArgumentType.longArg(), Long.class));
        VALIDATION_MAP.put("long_range", new Entry<>(2, opt -> AliasedCommand.rangeArgumentType(opt, Long.class, Long::parseLong,
                LongArgumentType::longArg), Long.class));
        VALIDATION_MAP.put("float", new Entry<>(0, opt -> FloatArgumentType.floatArg(), Float.class));
        VALIDATION_MAP.put("float_range", new Entry<>(2, opt -> AliasedCommand.rangeArgumentType(opt, Float.class, Float::parseFloat,
                FloatArgumentType::floatArg), Float.class));
        VALIDATION_MAP.put("double", new Entry<>(0, opt -> DoubleArgumentType.doubleArg(), Double.class));
        VALIDATION_MAP.put("double_range", new Entry<>(2, opt -> AliasedCommand.rangeArgumentType(opt, Double.class,
                Double::parseDouble, DoubleArgumentType::doubleArg), Double.class));
        VALIDATION_MAP.put("boolean", new Entry<>(0, opt -> BoolArgumentType.bool(), Boolean.class));
        VALIDATION_MAP.put("word", new Entry<>(0, opt -> StringArgumentType.word(), String.class));
        VALIDATION_MAP.put("string", new Entry<>(0, opt -> StringArgumentType.string(), String.class));
        VALIDATION_MAP.put("regex", new Entry<>(1, opt -> StringArgumentType.string(), String.class));
        VALIDATION_MAP.put("selection", new Entry<>(-1, opt -> StringArgumentType.string(), String.class));
    }

    private final String alias;
    private final List<String> commands = new ArrayList<>();
    private final Set<String> args = new LinkedHashSet<>();
    private int argCount;
    private int permission;
    private boolean silent;
    private static final Pattern ARG = Pattern.compile("\\{\\$[^:]+(:[^}]+)?}");

    public AliasedCommand(String alias, String command, CommandDispatcher<ServerCommandSource> dispatcher) {
        this.alias = alias;
        this.commands.add(command);
        this.permission = ConfigOption.ALIAS_DEFAULT_PERMISSION.val();
        this.silent = ConfigOption.ALIAS_DEFAULT_SILENT.val();
        this.updateArgCount();
        this.register(dispatcher);
    }

    private AliasedCommand(String alias, int permission, boolean silent, CommandDispatcher<ServerCommandSource> dispatcher, Collection<String> commands) {
        this.alias = alias;
        this.commands.addAll(commands);
        this.permission = permission;
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

    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    public List<String> getCommands() {
        return this.commands;
    }

    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        if (!AliasManager.ALIASES.containsKey(this.alias)) {
            AliasManager.ALIASES.put(this.alias, this);
        }
        ArgumentBuilder<ServerCommandSource, ?> tree = null;
        // Execution with required arguments
        if (this.argCount > 0) {
            // disgusting hack to build the command tree from the bottom up. thanks for the tip mojang
            String[] str = this.args.toArray(new String[0]);
            for (int i = str.length - 1; i >= 0; i--) {
                // arguments processed here
                String[] arg = str[i].split(":", 2);
                TechnicalToolbox.log("{}", arg);
                if (tree == null) {
                    String validation, opts;
                    if (arg.length == 2) {
                        String[] optargs = arg[1].split(" *\\| *", 2);
                        validation = optargs[0];
                        if (optargs.length == 2) {
                            opts = optargs[1];
                        }
                        else {
                            opts = "";
                        }
                    }
                    else {
                        validation = "word";
                        opts = "";
                    }
                    Entry<?> val = VALIDATION_MAP.getOrDefault(validation, VALIDATION_MAP.get("word"));
                    RequiredArgumentBuilder<ServerCommandSource, ?> base = CommandManager.argument(arg[0], val.argumentTypeProvider().apply(opts));

                    if ("selection".equals(validation)) {
                        base = base.suggests(((context, builder) -> CommandSource.suggestMatching(opts.split(","), builder)));
                    }
                    tree = base.executes(context -> {
                        String[] args = new String[str.length];
                        for (int j = 0; j < str.length; j++) {
                            if (val.args > 0 && val.args != opts.split(",").length) {
                                context.getSource().sendError(TextUtils.formattable("Mismatched args for " + validation + ": expected " +
                                        val.args + ", got " + opts.split(",").length));
                                return 1;
                            }
                            args[j] = context.getArgument(arg[0], val.clazz()).toString();
                        }
                        return this.execute(context, args, validation, opts);
                    });
                }
                else {
                    tree = CommandManager.argument(str[i], StringArgumentType.string()).then(tree);
                }
            }
            dispatcher.register((CommandManager.literal(this.alias)
                    .requires(source -> source.hasPermissionLevel(this.getPermission())))
                    .then(tree)
                    .executes(this::getCommandInfo));
        }
        // Execution if no args provided
        else {
            dispatcher.register((CommandManager.literal(this.alias)
                    .requires(source -> source.hasPermissionLevel(this.getPermission())))
                    .executes(this::execute));
        }
    }

    /**
     * Adds a command to the end of the current alias script and automatically updates argument count.
     * @param command full command to add.
     */
    public void addCommand(String command, MinecraftServer server) {
        this.commands.add(command);
        this.updateArgCount();
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
        this.updateArgCount();
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
        this.updateArgCount();
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
        for (String arg : this.args) {
            out.append(TextUtils.formattable("<" + arg + "> ").formatted(Formatting.GREEN));
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
     * Execute commands with args.
     * @param context command context
     * @param args input args
     */
    public int execute(CommandContext<ServerCommandSource> context, String[] args, String validation, String validationArgs) {
        for (String command : this.commands) {
            int i = 0;
            for (String arg : this.args) {
                String type = arg.split(":")[0];
                if ("regex".equals(validation) && !Pattern.compile(validationArgs).matcher(args[i]).matches()) {
                    context.getSource().sendError(TextUtils.formattable("Argument \"" + type + "\" must match regex \"" + validationArgs
                            + "\""));
                    return 1;
                }
                else if ("selection".equals(validation)) {
                    List<String> valid = List.of(arg.split(":")[1].split("\\|")[1]);
                    if (!valid.contains(args[i])) {
                        context.getSource().sendError(TextUtils.formattable("Argument \"" + type + "\" must be one of " + valid));
                        return 1;
                    }
                }
                command = command.replaceAll("\\{\\$" + type + "(:[^}]+)?}", args[i]);
                i++;
            }
            if (!this.executeCommand(context, command)) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Executes a command without any args.
     * @param context command context
     */
    private int execute(CommandContext<ServerCommandSource> context) {
        for (String command : this.commands) {
            if (!this.executeCommand(context, command)) {
                return 1;
            }
        }
        return 0;
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
     * Generic number range argument type
     */
    @SuppressWarnings("unchecked")
    private static <T extends Number> ArgumentType<T> rangeArgumentType(String opt, Class<T> clazz, Function<String, T> parse, BiFunction<T, T, ArgumentType<T>> argumentType) {
        T min, max;
        try {
            String[] arg = opt.split(",");
            if (arg.length != 2) {
                throw new Exception();
            }
            min = parse.apply(arg[0]);
            max = parse.apply(arg[1]);
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
            while ((line = bufferedReader.readLine()) != null) {
                if (!readingCommandState) {
                    String[] split = line.split(":");
                    if (split.length >= 2) {
                        switch (split[0].toLowerCase()) {
                            case "alias" -> alias = line.replaceFirst("(?i)Alias: *", "");
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
            new AliasedCommand(alias, permission, silent, server.getCommandManager().getDispatcher(), commands);
        }
        catch (IOException e) {
            TechnicalToolbox.warn("Something went wrong reading from file " + path);
        }
        return true;
    }

}
