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

    private static class NumberOperator {

        private boolean isLong = true;
        private long longVal;
        private double doubleVal;

        public NumberOperator(Number value) {
            if (value instanceof Integer || value instanceof Long) {
                this.longVal = (long) value;
            }
            else {
                this.isLong = false;
                this.doubleVal = (double) value;
            }
        }

        public static NumberOperator fromString(String s) {
            try {
                return new NumberOperator(Long.parseLong(s));
            }
            catch (NumberFormatException e) {
                try {
                    return new NumberOperator(Double.parseDouble(s));
                }
                catch (NumberFormatException f) {
                    return null;
                }
            }
        }

        public double getDoubleValue() {
            return this.isLong ? (double) this.longVal : this.doubleVal;
        }

        public boolean compare(String comparator, NumberOperator other) {
            switch (comparator) {
                case "=" -> {
                    return (this.isLong && other.isLong) ? this.longVal == other.longVal : this.getDoubleValue() == other.getDoubleValue();
                }
                case ">" -> {
                    return (this.isLong && other.isLong) ? this.longVal > other.longVal : this.getDoubleValue() > other.getDoubleValue();
                }
                case "<" -> {
                    return (this.isLong && other.isLong) ? this.longVal < other.longVal : this.getDoubleValue() < other.getDoubleValue();
                }
                case ">=" -> {
                    return (this.isLong && other.isLong) ? this.longVal >= other.longVal : this.getDoubleValue() >= other.getDoubleValue();
                }
                case "<=" -> {
                    return (this.isLong && other.isLong) ? this.longVal <= other.longVal : this.getDoubleValue() <= other.getDoubleValue();
                }
            }
            return false;
        }

    }

    private interface Instruction {

        int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, VariableDefinition> variables);

    }

    private record CommandInstruction(String command) implements Instruction {

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, VariableDefinition> variables) {
            String cmd = this.command;
            for (VariableDefinition var : variables.values()) {
                String arg = context.getArgument(var.name, var.type.clazz).toString();
                cmd = cmd.replaceAll("\\{\\$" + var.name + "}", arg);
            }
            return aliasedCommand.executeCommand(context, cmd) ? -1 : -2;
        }

        @Override
        public String toString() {
            return this.command;
        }

    }

    private static class IfInstruction extends JumpInstruction {

        private String cmp;
        private String left;
        private String right;
        public boolean valid = true;
        public String err = null;

        public IfInstruction(String expression, LinkedHashMap<String, VariableDefinition> vars) {
            super(-1);
            String[] comparators = expression.split(" *[<=>] *");
            if (comparators.length != 2) {
                this.err = "must be be of format [[if operator1 (>|>=|<|<=|==) operator2]]";
                this.valid = false;
            }
            else {
                String cmp = expression.replace(comparators[0], "").replace(comparators[1], "").strip();
                if (cmp.length() == 1 && "<=>".contains(cmp) || cmp.length() == 2 && cmp.matches("(<=|>=)")) {
                    this.cmp = cmp;
                    this.left = comparators[0];
                    this.right = comparators[1];
                    for (String s : new String[]{this.left, this.right}) {
                        if (s.startsWith("$")) {
                            String var = s.substring(1);
                            if (!vars.containsKey(var)) {
                                this.valid = false;
                                this.err = "no declaration/forward reference of variable \"" + var + "\"";
                                break;
                            }
                            if (!cmp.equals("=") && !Number.class.isAssignableFrom(vars.get(var).type.clazz)) {
                                this.valid = false;
                                this.err = "string types only support comparing equality";
                                break;
                            }
                        }
                    }
                }
                else {
                    this.err = "invalid comparator \"" + cmp + "\"";
                    this.valid = false;
                }
            }
        }

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, VariableDefinition> variables) {
            String l = left;
            String r = right;
            if (left.startsWith("$")) {
                VariableDefinition def = variables.get(left.substring(1));
                l = context.getArgument(def.name, def.type.clazz).toString();
            }
            if (right.startsWith("$")) {
                VariableDefinition def = variables.get(right.substring(1));
                r = context.getArgument(def.name, def.type.clazz).toString();
            }
            NumberOperator leftOp = NumberOperator.fromString(l);
            if (leftOp != null) {
                NumberOperator rightOp = NumberOperator.fromString(r);
                if (rightOp != null) {
                    return leftOp.compare(this.cmp, rightOp) ? -1 : this.jumpTo;
                }
            }
            return l.equals(r) ? -1 : this.jumpTo;
        }

        @Override
        public String toString() {
            return "if [" + this.left + this.cmp + this.right + "] else jmp " + this.jumpTo;
        }

    }

    private static class ElseInstruction extends JumpInstruction {

        public ElseInstruction(int jumpTo) {
            super(jumpTo);
        }

    }

    private static class IfJumpInstruction extends JumpInstruction {

        protected final int depth;

        public IfJumpInstruction(int jumpTo, int depth) {
            super(jumpTo);
            this.depth = depth;
        }

    }

    private static class JumpInstruction implements Instruction {

        protected int jumpTo;

        public JumpInstruction(int jumpTo) {
            this.jumpTo = jumpTo;
        }

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, VariableDefinition> variables) {
            return this.jumpTo;
        }


        @Override
        public String toString() {
            return "jmp " + this.jumpTo;
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
    private final List<Instruction> instructions = new ArrayList<>();
    private final LinkedHashMap<String, VariableDefinition> argumentDefinitions = new LinkedHashMap<>();
    private int permission;
    private boolean silent;
    private static final Pattern SAVED_ARGS = Pattern.compile("\\{\\$[^:]+(:[^}]+)?}");
    private static final Pattern STATEMENT = Pattern.compile("\\[\\[[^]]+]]");
    private static final Pattern STATEMENT_BEGIN = Pattern.compile("\\[\\[[^ \\]]*");

    public AliasedCommand(String alias, String command, CommandDispatcher<ServerCommandSource> dispatcher) {
        this.alias = alias;
        this.commands.add(command);
        this.permission = ConfigOption.ALIAS_DEFAULT_PERMISSION.val();
        this.silent = ConfigOption.ALIAS_DEFAULT_SILENT.val();
        this.register(dispatcher);
        AliasManager.ALIASES.put(this.alias, this);
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
        AliasManager.ALIASES.put(this.alias, this);
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
        Stack<Instruction> controlFlowStack = new Stack<>();
        int address = 0, depth = 0;
        for (int i = 0; i < this.commands.size(); i++) {
            String s = this.commands.get(i);
            if (!s.isEmpty()) {
                String cmd = s.strip(), c;
                Matcher m = STATEMENT.matcher(cmd);
                if (m.find() && (c = m.group()).equals(s.strip())) {
                    m = STATEMENT_BEGIN.matcher(c);
                    if (m.find()) {
                        String ctrl = m.group().replaceFirst("\\[\\[", "");
                        switch (ctrl) {
                            case "if" -> {
                                depth++;
                                String instr = c.replace("[[if", "").replace("]]", "").strip();
                                IfInstruction instruction = new IfInstruction(instr, this.argumentDefinitions);
                                if (!instruction.valid) {
                                    TechnicalToolbox.log("{} - line {}: {}", this.alias, i, instruction.err);
                                    return false;
                                }
                                this.instructions.add(instruction);
                                controlFlowStack.add(instruction);
                            }
                            case "elif" -> {
                                if (controlFlowStack.peek() instanceof IfInstruction instruction) {
                                    instruction.jumpTo = ++address;
                                    controlFlowStack.pop();
                                    Instruction jumpInstruction = new IfJumpInstruction(-1, depth);
                                    this.instructions.add(jumpInstruction);
                                    controlFlowStack.add(jumpInstruction);
                                    String instr = c.replace("[[elif", "").replace("]]", "").strip();
                                    IfInstruction newInstruction = new IfInstruction(instr, this.argumentDefinitions);
                                    if (!newInstruction.valid) {
                                        TechnicalToolbox.log("{} - line {}: {}", this.alias, i, newInstruction.err);
                                        return false;
                                    }
                                    this.instructions.add(newInstruction);
                                    controlFlowStack.add(newInstruction);
                                }
                                else {
                                    TechnicalToolbox.log("{} - line {}: [[elif]] must follow an [[if/elif]]", this.alias, i);
                                    return false;
                                }
                            }
                            case "else" -> {
                                if (controlFlowStack.peek() instanceof IfInstruction instruction) {
                                    instruction.jumpTo = address;
                                    controlFlowStack.pop();
                                    Instruction newInstruction = new ElseInstruction(address);
                                    this.instructions.add(newInstruction);
                                    controlFlowStack.add(newInstruction);
                                }
                                else {
                                    TechnicalToolbox.log("{} - line {}: [[else]] must follow an [[if/elif]]", this.alias, i);
                                    return false;
                                }
                            }
                            case "end" -> {
                                if (controlFlowStack.peek() instanceof JumpInstruction instruction) {
                                    instruction.jumpTo = address--;
                                    controlFlowStack.pop();
                                    while (!controlFlowStack.isEmpty() && controlFlowStack.peek() instanceof IfJumpInstruction instruction1) {
                                        instruction1.jumpTo = address + 1;
                                        controlFlowStack.pop();
                                    }
                                }
                                else {
                                    return false;
                                }
                            }
                            default -> {
                                TechnicalToolbox.log("{} - line {}: invalid statement [[{}]]", this.alias, i, ctrl);
                                return false;
                            }
                        }
                    }
                }
                else {
                    this.instructions.add(new CommandInstruction(cmd));
                }
                address++;
            }
        }
        TechnicalToolbox.log("/{} compiled", this.alias);
        for (int i = 0; i < this.instructions.size(); i++) {
            TechnicalToolbox.log("{}: {}", i, this.instructions.get(i));
        }
        return true;
    }

    public boolean register(CommandDispatcher<ServerCommandSource> dispatcher) {
        ArgumentBuilder<ServerCommandSource, ?> tree = null;
        // Execution with required arguments
        if (!this.compile()) {
            return false;
        }
        if (!this.argumentDefinitions.isEmpty()) {
            // the horror
            VariableDefinition[] vars = this.argumentDefinitions.values().toArray(new VariableDefinition[0]);
            for (int i = vars.length - 1; i >= 0; i--) {
                VariableDefinition def = vars[i];
                // arguments processed here
                RequiredArgumentBuilder<ServerCommandSource, ?> base = CommandManager.argument(def.name, def.getArgumentType());
                if ("selection".equals(def.typeName)) {
                    base = base.suggests(((context, builder) -> CommandSource.suggestMatching(def.args, builder)));
                }
                if (tree == null) {
                    tree = base.executes(this::execute);
                }
                else {
                    tree = base.then(tree);
                }
            }
            dispatcher.register((CommandManager.literal(this.alias)
                    .requires(source -> source.hasPermissionLevel(this.getPermission())))
                    .then(tree)
                    .executes(this::getCommandInfo));
        }
        // Execution if no arg provided
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
    public void addCommand(ServerCommandSource source,  String command) {
        this.commands.add(command);
        this.refresh(source);
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
    public MutableText removeCommand(ServerCommandSource source, int line) {
        if (this.commands.size() <= 1) {
            return TextUtils.formattable("Can't remove the last line in an alias");
        }
        if (line < 1 || line - 1 > this.commands.size()) {
            return TextUtils.formattable("Line index " + line + " out of bounds");
        }
        this.commands.remove(line - 1);
        this.refresh(source);
        return null;
    }

    /**
     * Inserts a line at a position, moving lines below down
     * @param line command string to be inserted
     * @param num line number to insert at (or before)
     * @return an error message if unsuccessful
     */
    public MutableText insert(ServerCommandSource source, String line, int num) {
        if (num < 1 || num - 1 > this.commands.size()) {
            return TextUtils.formattable("Line index " + num + " out of bounds");
        }
        this.commands.add(num - 1, line);
        this.refresh(source);
        return null;
    }

    /**
     * @return example of command usage
     */
    public MutableText getSyntax() {
        MutableText out = TextUtils.formattable("/").formatted(Formatting.YELLOW);
        out.append(TextUtils.formattable(this.alias).formatted(Formatting.YELLOW)).append(" ");
        for (VariableDefinition arg : this.argumentDefinitions.values()) {
            out.append(TextUtils.formattable("<").formatted(Formatting.GREEN));
            out.append(TextUtils.formattable(arg.name).formatted(Formatting.YELLOW));
            out.append(TextUtils.formattable(":").formatted(Formatting.GREEN));
            out.append(TextUtils.formattable(arg.typeName).formatted(Formatting.AQUA));
            if (arg.type.argc != 0) {
                out.append(TextUtils.formattable("[").formatted(Formatting.GREEN));
                for (int i = 0; i < arg.args.length; i++) {
                    out.append(TextUtils.formattable(arg.args[i]).formatted(Formatting.WHITE));
                    if (i != arg.args.length - 1) {
                        out.append(TextUtils.formattable(",").formatted(Formatting.GREEN));
                    }
                }
                out.append(TextUtils.formattable("]").formatted(Formatting.GREEN));
            }
            out.append(TextUtils.formattable("> ").formatted(Formatting.GREEN));
        }
        return out;
    }

    /**
     * Deregisters a command alias and resends the command tree.
     * @param server server to deregister commands from.
     */
    public void deregister(MinecraftServer server, boolean hard) {
        ServerUtil.removeCommandByName(server, this.alias);
        if (hard) {
            AliasManager.ALIASES.remove(this.alias);
        }
    }

    /**
     * Executes command on server with command permission level override enabled
     * @param context command context
     * @param command command to execute
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean executeCommand(CommandContext<ServerCommandSource> context, String command) {
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
     * Executes an alias from start to finish.
     * @param context command context
     */
    private int execute(CommandContext<ServerCommandSource> context) {
        LinkedHashMap<String, VariableDefinition> variableDefinitions = new LinkedHashMap<>(this.argumentDefinitions);
        for (VariableDefinition var : variableDefinitions.values()) {
            String arg = context.getArgument(var.name, var.type.clazz).toString();
            if ("selection".equals(var.typeName) && Arrays.stream(var.args).noneMatch(s -> s.equals(arg))) {
                context.getSource().sendError(TextUtils.formattable("Unsupported argument \"" + arg + "\": must be one of " +
                        Arrays.toString(var.args)));
                return 0;
            }
        }
        for (int i = 0; i < this.instructions.size(); i++) {
            int out = this.instructions.get(i).execute(this, context, variableDefinitions);
            if (out == -2) {
                return 0;
            }
            else if (out >= 0) {
                i = out - 1;
            }
        }
        return 1;
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
                source.sendError(TextUtils.formattable("Argument \"" + name + "\" already exists"));
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
        this.refresh(source);
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
            this.refresh(source);
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

    public void refresh(ServerCommandSource source) {
        this.deregister(source.getServer(), false);
        if (!this.register(source.getDispatcher())) {
            source.sendError(TextUtils.formattable("Failed to compile alias \"" + this.alias + "\", see logs for details"));
        }
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