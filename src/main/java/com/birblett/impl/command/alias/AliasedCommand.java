package com.birblett.impl.command.alias;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.config.ConfigOption;
import com.birblett.lib.command.CommandSourceModifier;
import com.birblett.lib.command.delay.AliasedCommandSource;
import com.birblett.util.ServerUtil;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
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
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data container for aliased commands/command scripts
 * <br><br><br>
 * what the boilerplate is this
 */
public class AliasedCommand {

    private String alias;
    private boolean compiled = false;
    private final List<String> commands = new ArrayList<>();
    private final List<Instruction> instructions = new ArrayList<>();
    private final LinkedHashMap<String, VariableDefinition> argumentDefinitions = new LinkedHashMap<>();
    private int permission;
    private boolean silent;
    public final boolean global;
    private static final Pattern SAVED_ARGS = Pattern.compile("\\{\\$[^:]+(:[^}]+)?}");
    private static final Pattern STATEMENT = Pattern.compile("\\[.*]");
    private static final Pattern STATEMENT_BEGIN = Pattern.compile("\\[[^\\ ]+");
    public static final HashMap<String, Entry<?>> ARGUMENT_TYPES = new HashMap<>();
    public String status = null;

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

    public AliasedCommand(String alias, String command, CommandDispatcher<ServerCommandSource> dispatcher) {
        this.global = false;
        this.alias = alias;
        this.commands.add(command);
        this.permission = ConfigOption.ALIAS_DEFAULT_PERMISSION.val();
        this.silent = ConfigOption.ALIAS_DEFAULT_SILENT.val();
        AliasManager.ALIASES.put(this.alias, this);
        this.register(dispatcher);
    }

    private AliasedCommand(String alias, int permission, boolean silent, CommandDispatcher<ServerCommandSource> dispatcher, Collection<String> commands, Collection<VariableDefinition> arguments, boolean global) {
        this.global = global;
        this.alias = alias;
        this.commands.addAll(commands);
        List<String> args = new ArrayList<>();
        for (VariableDefinition var : arguments) {
            if (var.name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                this.argumentDefinitions.put(var.name, var);
            }
            else {
                args.add(var.name);
            }
        }
        if (!args.isEmpty()) {
            TechnicalToolbox.log("{}: couldn't parse argument(s) {}", alias, args);
        }
        this.permission = permission;
        this.silent = silent;
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

    /**
     * Defines variable type, primarily for use with arguments.
     * @param argc number of arguments required
     * @param argumentTypeProvider provides an argument type to be passed to an argument builder
     * @param clazz class used to retrieve arguments
     */
    public record Entry<T>(int argc, Function<String[], ArgumentType<T>> argumentTypeProvider, Class<T> clazz) {}

    /**
     * Defines various traits related to variables, such as its name, type, and expected arguments.
     */
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

    /**
     * The basic variable for use during program execution. Holds a definition and a value.
     * @param type
     * @param value
     */
    private record Variable(VariableDefinition type, Object value) {}

    /**
     * The basic interface for operators in expressions - essentially a data container.
     * getValue returns raw data, operation specifies how it interfaces with other
     * Operators.
     */
    public interface Operator {

        Object getValue();
        Operator operation(String operator, Operator other);
        boolean compare(String comparator, Operator other);

    }

    /**
     * Supports operations between two numbers, or a number and a string. Internal calculations
     * handled via long and double values where necessary.
     */
    private static class NumberOperator implements Operator {

        private boolean isLong = true;
        private long longVal;
        private double doubleVal;

        public NumberOperator(Number value) {
            if (value instanceof Integer || value instanceof Long) {
                this.longVal = value.longValue();
            }
            else {
                this.isLong = false;
                this.doubleVal = value.doubleValue();
            }
        }

        public static NumberOperator fromString(String s) {
            try {
                return new NumberOperator(Long.parseLong(s));
            }
            catch (NumberFormatException e) {
                try {
                    if (s.endsWith("s")) {
                        return new NumberOperator(Double.parseDouble(s.substring(0, s.length() - 1)));
                    }
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

        @Override
        public Object getValue() {
            return this.isLong ? this.longVal : this.doubleVal;
        }

        public Operator operation(String operator, Operator other) {
            if (other instanceof NumberOperator second) {
                switch (operator) {
                    case "+" -> {
                        return new NumberOperator((this.isLong && second.isLong) ? this.longVal + second.longVal : this.getDoubleValue() +
                                second.getDoubleValue());
                    }
                    case "-" -> {
                        return new NumberOperator((this.isLong && second.isLong) ? this.longVal - second.longVal : this.getDoubleValue() -
                                second.getDoubleValue());
                    }
                    case "*" -> {
                        return new NumberOperator((this.isLong && second.isLong) ? this.longVal * second.longVal : this.getDoubleValue() *
                                second.getDoubleValue());
                    }
                    case "/" -> {
                        return new NumberOperator((this.isLong && second.isLong) ? this.longVal / second.longVal : this.getDoubleValue() /
                                second.getDoubleValue());
                    }
                    case "^" -> {
                        return new NumberOperator((this.isLong && second.isLong) ? Math.pow(second.longVal, this.longVal) :
                                Math.pow(second.getDoubleValue(), this.getDoubleValue()));
                    }
                    case "%" -> {
                        return new NumberOperator((this.isLong && second.isLong) ? second.longVal % this.longVal :
                                second.getDoubleValue() % this.getDoubleValue());
                    }
                }
            }
            else if (other instanceof StringOperator str) {
                return new StringOperator(this.getValue().toString()).operation("+", str);
            }
            return null;
        }

        public boolean compare(String comparator, Operator other) {
            if (other instanceof NumberOperator num) {
                switch (comparator) {
                    case "=" -> {
                        return (this.isLong && num.isLong) ? this.longVal == num.longVal : this.getDoubleValue() == num.getDoubleValue();
                    }
                    case ">" -> {
                        return (this.isLong && num.isLong) ? this.longVal > num.longVal : this.getDoubleValue() > num.getDoubleValue();
                    }
                    case "<" -> {
                        return (this.isLong && num.isLong) ? this.longVal < num.longVal : this.getDoubleValue() < num.getDoubleValue();
                    }
                    case ">=" -> {
                        return (this.isLong && num.isLong) ? this.longVal >= num.longVal : this.getDoubleValue() >= num.getDoubleValue();
                    }
                    case "<=" -> {
                        return (this.isLong && num.isLong) ? this.longVal <= num.longVal : this.getDoubleValue() <= num.getDoubleValue();
                    }
                }
            }
            else if (other instanceof StringOperator str) {
                return str.compare("=", this);
            }
            return false;
        }

    }

    private record StringOperator(String str) implements Operator {

        @Override
        public Object getValue() {
            return this.str;
        }

        @Override
        public StringOperator operation(String operator, Operator second) {
            return new StringOperator(second.getValue() + this.str);
        }

        @Override
        public boolean compare(String comparator, Operator other) {
            return this.str.equals(other.getValue().toString());
        }

    }

    /**
     * The basic instruction interface; only requires an execute method; everything else is
     * instruction-specific.
     */
    private interface Instruction {

        default int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            return -1;
        }

    }

    /**
     * Holds a single command; does text replacement for variables on execution.
     * @param command
     */
    private record CommandInstruction(String command) implements Instruction {

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            String cmd = this.command;
            for (String var : variables.keySet()) {
                cmd = cmd.replaceAll("\\{\\$" + var + "}", variables.get(var).value.toString());
            }
            return aliasedCommand.executeCommand(context, cmd) ? -1 : -2;
        }

        @Override
        public String toString() {
            return this.command;
        }

    }

    /**
     * Contains methods and values for parsing expressions i.e. for if/let/return statements
     */
    private interface ExpressionParser {

        record ExpressionOperator(String op, int precedence) {}
        HashMap<String, Integer> PRECEDENCE = new HashMap<>();
        HashMap<Class<?>, Integer> TYPE_MAP = new HashMap<>();
        HashMap<String, Integer> TYPE_VALUE_MAP = new HashMap<>();
        HashMap<Integer, String> INV_VALUE_MAP = new HashMap<>();
        Pattern TOKEN = Pattern.compile("((?<!\\\\)\".*?(?<!\\\\)\"|[0-9]+[.][0-9]+[fF]?|[0-9]+[fF]?|[()+\\-%*/^]|[a-zA-Z_][a-zA-Z0-9_]*)");

        default Integer parseExpression(String expr, Integer type, List<LinkedHashMap<String, VariableDefinition>> vars, Queue<Object> post) {
            int inferredType = type != null ? type : 0;
            Stack<ExpressionOperator> stack = new Stack<>();
            Matcher m = TOKEN.matcher(expr);
            boolean lastOperand = true;
            int depth = 0;
            while (m.find()) {
                String token = m.group();
                if (token.startsWith("\"") && token.endsWith("\"")) {
                    if (type == null) {
                        inferredType = 4;
                    }
                    else if (type < 4) {
                        this.error("can't forcibly coerce string type to numerical value");
                        return null;
                    }
                }
                switch (token) {
                    case " " -> {}
                    case "(" -> {
                        if (!lastOperand) {
                            this.error("operand can't directly follow another operand");
                            return null;
                        }
                        depth++;
                    }
                    case ")" -> {
                        if (lastOperand) {
                            this.error("operator can't directly follow another operator");
                            return null;
                        }
                        depth--;
                    }
                    case "+", "-", "*", "/", "^", "%" -> {
                        if (lastOperand) {
                            this.error("operator can't directly follow another operator");
                            return null;
                        }
                        lastOperand = true;
                        int p = PRECEDENCE.get(token) + 3 * depth;
                        while (!stack.isEmpty() && stack.peek().precedence >= p) {
                            post.add(stack.pop().op);
                        }
                        stack.push(new ExpressionOperator(token, p));
                    }
                    default -> {
                        if (!lastOperand) {
                            this.error("operand can't directly follow another operand");
                            return null;
                        }
                        NumberOperator num = NumberOperator.fromString(token);
                        if (num == null) {
                            if (token.startsWith("\"") && token.endsWith("\"")) {
                                inferredType = 4;
                                post.add(new StringOperator(token.substring(1, token.length() - 1)));
                            }
                            else {
                                boolean valid = false;
                                for (LinkedHashMap<String, VariableDefinition> varMap : vars) {
                                    if (varMap.containsKey(token)) {
                                        valid = true;
                                        inferredType = Math.max(inferredType, TYPE_MAP.getOrDefault(varMap.get(token).type.clazz, 4));
                                        post.add(token);
                                        break;
                                    }
                                }
                                if (!valid) {
                                    this.error("no declaration/forward reference of variable \"" + token + "\"");
                                    return null;
                                }
                            }
                        }
                        else {
                            if (type == null) {
                                Number n = (Number) num.getValue();
                                if (token.endsWith("f")) {
                                    inferredType = 2;
                                }
                                else {
                                    if (inferredType != 0 || n.intValue() != num.getDoubleValue()) {
                                        if (inferredType <= 1 && n.longValue() == num.getDoubleValue()) {
                                            inferredType = 1;
                                        } else {
                                            inferredType = 3;
                                        }
                                    }
                                }
                            }
                            post.add(num);
                        }
                        lastOperand = false;
                    }
                }
            }
            if (lastOperand) {
                this.error("expression contains operator without operand");
                return null;
            }
            while (!stack.isEmpty()) {
                post.add(stack.pop().op);
            }
            if (depth != 0) {
                this.error("mismatched parentheses in expression");
                return null;
            }
            boolean hasString = false;
            boolean hasNonAddition = false;
            for (Object o : post) {
                if (o instanceof String s && "-*/^%".contains(s)) {
                    hasNonAddition = true;
                }
                else if (o instanceof StringOperator) {
                    inferredType = 4;
                    hasString = true;
                }
            }
            if (hasString && hasNonAddition) {
                this.error("string type only supports concatenation");
                return null;
            }
            return inferredType;
        }

        default Operator evaluate(Queue<Object> post, LinkedHashMap<String, Variable> variables) {
            Queue<Object> postfix = new LinkedList<>(post);
            Stack<Operator> eval = new Stack<>();
            if (!postfix.isEmpty()) {
                while (!postfix.isEmpty()) {
                    Object o = postfix.poll();
                    if (o instanceof String tok) {
                        if ("+-*/^%".contains(tok)) {
                            switch (tok) {
                                case "*", "+", "^", "%" -> eval.push(eval.pop().operation(tok, eval.pop()));
                                case "/", "-" -> {
                                    Operator arg = eval.pop();
                                    eval.push(eval.pop().operation(tok, arg));
                                }
                            }
                        }
                        else {
                            Variable v = variables.get(tok);
                            if (Number.class.isAssignableFrom(v.type.type.clazz)) {
                                eval.push(new NumberOperator((Number) v.value));
                            }
                            else {
                                eval.push(new StringOperator(v.value.toString()));
                            }
                        }
                    }
                    else if (o instanceof Operator op) {
                        eval.push(op);
                    }
                }
            }
            return eval.peek();
        }

        void error(String s);

    }

    static {
        ExpressionParser.PRECEDENCE.put("+", 0);
        ExpressionParser.PRECEDENCE.put("-", 0);
        ExpressionParser.PRECEDENCE.put("*", 1);
        ExpressionParser.PRECEDENCE.put("/", 1);
        ExpressionParser.PRECEDENCE.put("%", 1);
        ExpressionParser.PRECEDENCE.put("^", 2);
        ExpressionParser.TYPE_MAP.put(Integer.class, 0);
        ExpressionParser.TYPE_MAP.put(Long.class, 1);
        ExpressionParser.TYPE_MAP.put(Float.class, 2);
        ExpressionParser.TYPE_MAP.put(Double.class, 3);
        ExpressionParser.TYPE_MAP.put(String.class, 4);
        ExpressionParser.TYPE_VALUE_MAP.put("int", 0);
        ExpressionParser.TYPE_VALUE_MAP.put("long", 1);
        ExpressionParser.TYPE_VALUE_MAP.put("float", 2);
        ExpressionParser.TYPE_VALUE_MAP.put("double", 3);
        ExpressionParser.TYPE_VALUE_MAP.put("string", 4);
        ExpressionParser.INV_VALUE_MAP.put(0, "int");
        ExpressionParser.INV_VALUE_MAP.put(1, "long");
        ExpressionParser.INV_VALUE_MAP.put(2, "float");
        ExpressionParser.INV_VALUE_MAP.put(3, "double");
        ExpressionParser.INV_VALUE_MAP.put(4, "string");
    }

    /**
     * Is capable of assigning to variables and evaluating the value of expressions. Handles
     * order of operations and parentheses by converting to postfix and doing some preprocessing
     * during the compilation step, and interprets the postfix instructions via a stack.
     */
    private static class AssignmentInstruction implements ExpressionParser, Instruction {

        protected boolean valid = true;
        protected int type = 0;
        private final String assignVar;
        protected String err = null;
        protected final Queue<Object> post = new LinkedList<>();

        public AssignmentInstruction(String assignVar, String expr, List<LinkedHashMap<String, VariableDefinition>> vars) {
            String[] assn = assignVar.split(" ");
            if (assn.length == 2) {
                this.assignVar = assn[1];
            }
            else if (assn.length != 1) {
                this.assignVar = "";
                this.err = "expected 1-2 arguments for assignment, got " + assn.length;
                this.valid = false;
                return;
            }
            else {
                this.assignVar = assignVar;
            }
            if (!this.assignVar.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                this.err = "invalid variable name " + this.assignVar;
                this.valid = false;
                return;
            }
            boolean newAssignment = true;
            LinkedHashMap<String, VariableDefinition> map = null;
            for (LinkedHashMap<String, VariableDefinition> varMap : vars) {
                if (varMap.containsKey(this.assignVar)) {
                    newAssignment = false;
                    map = varMap;
                    break;
                }
            }
            if (map != null) {
                this.type = TYPE_MAP.getOrDefault(map.get(this.assignVar).type.clazz, 4);
            }
            Integer forcedType = (!newAssignment && assn.length == 2) ? TYPE_VALUE_MAP.get(assn[0]) : null;
            Integer type = this.parseExpression(expr, forcedType, vars, this.post);
            if (type == null) {
                this.valid = false;
                return;
            }
            this.type = type;
            String varType = INV_VALUE_MAP.getOrDefault(this.type, "string");
            if (newAssignment) {
                vars.getLast().put(this.assignVar, new VariableDefinition(this.assignVar, varType, new String[0]));
            }
            else {
                map.put(this.assignVar, new VariableDefinition(this.assignVar, varType, new String[0]));
            }
        }

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            Operator o = this.evaluate(this.post, variables);
            if (!variables.containsKey(this.assignVar)) {
                variables.put(this.assignVar, new Variable(new VariableDefinition(this.assignVar, INV_VALUE_MAP
                        .getOrDefault(this.type, "string"), new String[0]), null));
            }
            if (o instanceof NumberOperator n) {
                variables.put(this.assignVar, new Variable(variables.get(this.assignVar).type, n.getValue()));
            }
            else if (o instanceof StringOperator s) {
                variables.put(this.assignVar, new Variable(variables.get(this.assignVar).type, s.str));
            }
            return -1;
        }

        @Override
        public void error(String s) {
            this.err = s;
        }

    }

    /**
     * The basic jump instruction; tells the interpreter to jump to a specific index.
     */
    private static class JumpInstruction implements Instruction {

        protected int jumpTo;

        public JumpInstruction(int jumpTo) {
            this.jumpTo = jumpTo;
        }

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            return this.jumpTo;
        }

        @Override
        public String toString() {
            return "jmp " + this.jumpTo;
        }

    }

    /**
     * Tests a condition; if it fails, it jumps to after the if statement ends. Jumping past
     * [elif/else/end] when successful is handled via {@link AliasedCommand.IfJumpInstruction}.
     */
    private static class IfInstruction extends JumpInstruction implements ExpressionParser {

        protected String name = "if";
        protected String cmp;
        private final Queue<Object> left = new LinkedList<>();
        private final Queue<Object> right = new LinkedList<>();
        protected String err = null;
        public boolean valid = true;

        public IfInstruction(String expression, List<LinkedHashMap<String, VariableDefinition>> vars) {
            super(-1);
            String[] comparators = expression.split(" *[<=>] *");
            if (comparators.length != 2) {
                this.err = "must be be of format [" + this.name + " operator1 (>|>=|<|<=|==) operator2]";
                this.valid = false;
                return;
            }
            String cmp = expression.replace(comparators[0], "").replace(comparators[1], "").strip();
            if (cmp.length() == 1 && "<=>".contains(cmp) || cmp.length() == 2 && cmp.matches("(<=|>=)")) {
                this.cmp = cmp;
                Integer[] type = {0, 0};
                type[0] = this.parseExpression(comparators[0], null, vars, this.left);
                if (type[0] == null) {
                    this.valid = false;
                    return;
                }
                type[1] = this.parseExpression(comparators[1], null, vars, this.right);
                if (type[1] == null) {
                    this.valid = false;
                    return;
                }
                if (!Objects.equals(type[0], type[1]) && (type[0] == 4 || type[1] == 4) && !"=".equals(this.cmp)) {
                    this.err = "string type only supports comparison of equality";
                }
            }
            else {
                this.err = "invalid comparator \"" + cmp + "\"";
                this.valid = false;
            }
        }

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            Operator l = this.evaluate(this.left, variables);
            Operator r = this.evaluate(this.right, variables);
            return l.compare(this.cmp, r) ? -1 : this.jumpTo;
        }

        @Override
        public String toString() {
            return "if [" + this.left + this.cmp + this.right + "] else jmp " + this.jumpTo;
        }

        @Override
        public void error(String s) {
            this.err = s;
        }

    }

    /**
     * A JumpInstruction with an extra depth field to tell the compiler not to overwrite other instructions when out of scope.
     */
    private static class IfJumpInstruction extends JumpInstruction {

        protected final int depth;

        public IfJumpInstruction(int jumpTo, int depth) {
            super(jumpTo);
            this.depth = depth;
        }

    }

    /**
     * For all intents and purposes, an IfInstruction, but [elif/else/end] make some exceptions for this
     */
    private static class WhileInstruction extends IfInstruction {

        protected int startAddress;

        public WhileInstruction(int startAddress, String expression, List<LinkedHashMap<String, VariableDefinition>> vars) {
            super(expression, vars);
            this.startAddress = startAddress;
            this.name = "while";
        }

    }

    private static class ReturnInstruction implements Instruction {

        public ReturnInstruction(String expression, List<LinkedHashMap<String, VariableDefinition>> vars) {
        }

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            return 0;
        }

    }

    /**
     * Logs an error and stores an error message.
     * @param line line number
     * @param err specific error mesasge
     * @return always false, inlined when compiler fails
     */
    private boolean compileError(int line, String err) {
        TechnicalToolbox.log("Failed to compile /{} - line {}: {}", this.alias, line + 1, err);
        this.status = "Line " + line + ": " + err;
        return false;
    }

    /**
     * Attempts to compile the currently stored alias; control flow/assignment are formatted as
     * [statement], everything else that does not match the regex will be interpreted as a command
     * @return false if it fails to compile
     */
    private boolean compile() {
        this.instructions.clear();
        Stack<Instruction> controlFlowStack = new Stack<>();
        List<LinkedHashMap<String, VariableDefinition>> scope = new ArrayList<>();
        scope.add(new LinkedHashMap<>(this.argumentDefinitions));
        int address = 0, depth = 0;
        for (int i = 0; i < this.commands.size(); i++) {
            String s = this.commands.get(i);
            if (!s.isEmpty()) {
                String cmd = s.strip(), c;
                Matcher m = STATEMENT.matcher(cmd);
                if (m.find() && (c = m.group()).equals(s.strip())) {
                    m = STATEMENT_BEGIN.matcher(c);
                    if (m.find()) {
                        String ctrl = m.group().replaceFirst("\\[", "");
                        if (ctrl.endsWith("]")) {
                            ctrl = ctrl.substring(0, ctrl.length() - 1);
                        }
                        switch (ctrl) {
                            // handles assignment - handles expressions using either longs or doubles depending on which is necessary,
                            // then casts to matching type on assignment
                            case "let" -> {
                                String[] instr = c.substring(1, c.length() - 1).replaceFirst("let", "").strip()
                                        .split("=", 2);
                                if (instr.length != 2) {
                                    return this.compileError(i, "assignment must be of form [let var = (expression)]");
                                }
                                AssignmentInstruction e = new AssignmentInstruction(instr[0].strip(), instr[1], scope);
                                if (!e.valid) {
                                    return this.compileError(i, e.err);
                                }
                                this.instructions.add(e);
                            }
                            // tests a condition; if it fails jump to the next elif/else
                            case "if" -> {
                                depth++;
                                scope.add(new LinkedHashMap<>());
                                String instr =  c.substring(1, c.length() - 1).replaceFirst("if", "").strip();
                                IfInstruction instruction = new IfInstruction(instr, scope);
                                if (!instruction.valid) {
                                    return this.compileError(i, instruction.err);
                                }
                                this.instructions.add(instruction);
                                controlFlowStack.add(instruction);
                            }
                            // sets previous if/elif conditional jump to the current address and creates an IfJump instruction, which will
                            // point to the next [end] block when that gets compiled
                            case "elif" -> {
                                if (!controlFlowStack.isEmpty() && controlFlowStack.peek() instanceof IfInstruction instruction &&
                                        !(instruction instanceof WhileInstruction)) {
                                    instruction.jumpTo = ++address;
                                    controlFlowStack.pop();
                                    Instruction jumpInstruction = new IfJumpInstruction(-1, depth);
                                    this.instructions.add(jumpInstruction);
                                    controlFlowStack.add(jumpInstruction);
                                    String instr =  c.substring(1, c.length() - 1).replaceFirst("elif", "").strip();
                                    IfInstruction newInstruction = new IfInstruction(instr, scope);
                                    if (!newInstruction.valid) {
                                        return this.compileError(i, newInstruction.err);
                                    }
                                    this.instructions.add(newInstruction);
                                    controlFlowStack.add(newInstruction);
                                    scope.removeLast();
                                    scope.add(new LinkedHashMap<>());
                                }
                                else {
                                    return this.compileError(i, "[elif] must follow an [if/elif]");
                                }
                            }
                            // strictly speaking this does not need an explicit instruction but i was lazy and didn't want to do extra
                            // validation for [end]
                            case "else" -> {
                                if (!controlFlowStack.isEmpty() && controlFlowStack.peek() instanceof IfInstruction instruction &&
                                        !(instruction instanceof WhileInstruction)) {
                                    instruction.jumpTo = address + 1;
                                    controlFlowStack.pop();
                                    Instruction newInstruction = new JumpInstruction(address);
                                    this.instructions.add(newInstruction);
                                    controlFlowStack.add(newInstruction);
                                    scope.removeLast();
                                    scope.add(new LinkedHashMap<>());
                                }
                                else {
                                    return this.compileError(i, "{} - line {}: [else] must follow an [if/elif]");
                                }
                            }
                            // basically identical to if i actually haven't tested this and have no idea if it works or not
                            case "while" -> {
                                depth++;
                                scope.add(new LinkedHashMap<>());
                                String instr =  c.substring(1, c.length() - 1).replaceFirst("while", "").strip();
                                WhileInstruction instruction = new WhileInstruction(address, instr, scope);
                                if (!instruction.valid) {
                                    return this.compileError(i, instruction.err);
                                }
                                this.instructions.add(instruction);
                                controlFlowStack.add(instruction);
                            }
                            // [end] handles all control flow so compilation depends on whatever happens to be on the stack
                            case "end" -> {
                                depth--;
                                scope.removeLast();
                                if (controlFlowStack.isEmpty()) {
                                    return this.compileError(i, "[end] does not enclose any control block");
                                }
                                else if (controlFlowStack.peek() instanceof WhileInstruction instruction) {
                                    this.instructions.add(new JumpInstruction(instruction.startAddress));
                                    instruction.jumpTo = address + 1;
                                    controlFlowStack.pop();
                                }
                                else if (controlFlowStack.peek() instanceof JumpInstruction instruction) {
                                    instruction.jumpTo = address--;
                                    controlFlowStack.pop();
                                    // processes all the previous if/elif/else chains, so they correctly skip over elif/else after if
                                    // their condition passes
                                    while (!controlFlowStack.isEmpty() && controlFlowStack.peek() instanceof IfJumpInstruction instruction1) {
                                        instruction1.jumpTo = address + 1;
                                        controlFlowStack.pop();
                                    }
                                }
                                else {
                                    // is this even reachable i have no idea
                                    return false;
                                }
                            }
                            default -> {
                                return this.compileError(i, "invalid statement [" + ctrl +"]");
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
        while (!controlFlowStack.isEmpty()) {
            scope.removeLast();
            if (controlFlowStack.peek() instanceof WhileInstruction instruction) {
                this.instructions.add(new JumpInstruction(instruction.startAddress));
                instruction.jumpTo = address + 1;
                controlFlowStack.pop();
            }
            else if (controlFlowStack.peek() instanceof JumpInstruction instruction) {
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
            address++;
        }
        /*
        TechnicalToolbox.log("/{} compiled", this.alias);
        for (int i = 0; i < this.instructions.size(); i++) {
            TechnicalToolbox.log("{}: {}", i, this.instructions.get(i));
        }
        */
        return true;
    }

    /**
     * Attempts to register a command, and sends compiler errors to the source.
     * @param source command source to send feedback to
     * @return whether compilation was successful or not
     */
    public boolean register(ServerCommandSource source) {
        boolean b = this.register(source.getDispatcher());
        if (!b) {
            source.sendError(TextUtils.formattable("Failed to compile:"));
            source.sendError(TextUtils.formattable(this.status));
        }
        return b;
    }

    /**
     * Compiles the alias and if successful registers it as an executable command.
     * @param dispatcher dispatcher to register to
     * @return true if successful, false if compilation failed
     */
    public boolean register(CommandDispatcher<ServerCommandSource> dispatcher) {
        if (!AliasManager.ALIASES.containsKey(this.alias)) {
            AliasManager.ALIASES.put(this.alias, this);
        }
        // Compile first, if compilation fails then it does nothing
        if (!(this.compiled = this.compile())) {
            return false;
        }
        this.status = "Compiled successfully";
        List<ArgumentBuilder<ServerCommandSource, ?>> tree = new ArrayList<>();
        // Execution with required arguments
        if (!this.argumentDefinitions.isEmpty()) {
            // the horror
            VariableDefinition[] vars = this.argumentDefinitions.values().toArray(new VariableDefinition[0]);
            for (int i = vars.length - 1; i >= 0; i--) {
                VariableDefinition def = vars[i];
                List<ArgumentBuilder<ServerCommandSource, ?>> baseNodes = new ArrayList<>();
                // selection adds each option as its own literal, needs to modify command source to preserve arguments
                if ("selection".equals(def.typeName)) {
                    for (String s : def.args) {
                        baseNodes.add(CommandManager.literal(s).requires(source -> {
                            ((CommandSourceModifier) source).technicalToolbox$addSelector(def.name, s);
                            return true;
                        }));
                    }
                }
                else {
                    baseNodes.add(CommandManager.argument(def.name, def.getArgumentType()));
                }
                // build command tree by layer, from the bottom up
                if (tree.isEmpty()) {
                    for (ArgumentBuilder<ServerCommandSource, ?> base : baseNodes) {
                        tree.add(base.executes(this::execute));
                    }
                }
                else {
                    for (ArgumentBuilder<ServerCommandSource, ?> base : baseNodes) {
                        for (ArgumentBuilder<ServerCommandSource, ?> treeNode : tree) {
                            base = base.then(treeNode);
                        }
                    }
                    tree = baseNodes;
                }
            }
            LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal(this.alias)
                            .requires(source -> source.hasPermissionLevel(this.getPermission()));
            for (ArgumentBuilder<ServerCommandSource, ?> base : tree) {
                root = root.then(base);
            }
            dispatcher.register(root.executes(this::getCommandInfo));
        }
        // Execution if no arg provided
        else {
            dispatcher.register((CommandManager.literal(this.alias)
                    .requires(source -> source.hasPermissionLevel(this.getPermission())))
                    .executes(this::execute));
        }
        return true;
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
     * Executes an alias from start to finish. Configured to interpret {@link AliasedCommand.Instruction}.
     * @param context command context
     */
    private int execute(CommandContext<ServerCommandSource> context) {
        LinkedHashMap<String, Variable> variableDefinitions = new LinkedHashMap<>();
        List<AliasedCommand.Instruction> instructions = List.copyOf(this.instructions);
        AliasedCommandSource source = (AliasedCommandSource) context.getSource();
        // load arguments locally
        for (VariableDefinition var : this.argumentDefinitions.values()) {
            if ("selection".equals(var.typeName)) {
                String arg = ((CommandSourceModifier) context.getSource()).technicalToolbox$getSelectorArgument(var.name);
                variableDefinitions.put(var.name, new Variable(var, arg));
            }
            else {
                Object arg = context.getArgument(var.name, var.type.clazz);
                variableDefinitions.put(var.name, new Variable(var, arg));
            }
        }
        int i;
        // main loop for running instructions; opcode of -2 is error, -1 is donothing, >=0 is an instruction index to jump to
        for (i = 0; i < instructions.size() && (ConfigOption.ALIAS_INSTRUCTION_LIMIT.val() == -1 ||
                source.technicalToolbox$getInstructionCount() < ConfigOption.ALIAS_INSTRUCTION_LIMIT.val()); i++,
                source.technicalToolbox$AddToInstructionCount(1)) {
            int out = instructions.get(i).execute(this, context, variableDefinitions);
            if (out == -2) {
                return 0;
            }
            else if (out >= 0) {
                i = out - 1;
            }
        }
        if (i < instructions.size()) {
            context.getSource().sendError(TextUtils.formattable("Exceeded the instruction limit of " +
                    ConfigOption.ALIAS_INSTRUCTION_LIMIT.val()));
            return 0;
        }
        return 1;
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

    public boolean rename(CommandContext<ServerCommandSource> context, String name) {
        this.deregister(context.getSource().getServer(), true);
        this.alias = name;
        return this.register(context.getSource());
    }

    /**
     * Adds a line to the end of the current alias script and automatically updates argument count.
     * @param command full command to add.
     */
    public void addCommand(String command) {
        this.commands.add(command);
    }

    /**
     * Removes a line at the given position. Can't remove the last line of an alias.
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
        return null;
    }

    /**
     * Inserts a line at a position, moving lines below down
     * @param line command string to be inserted
     * @param num line number to insert at (or before)
     * @return an error message if unsuccessful
     */
    public MutableText insert(String line, int num) {
        if (num < 1 || num - 1 > this.commands.size()) {
            return TextUtils.formattable("Line index " + num + " out of bounds");
        }
        this.commands.add(num - 1, line);
        return null;
    }

    /**
     * @return simple command syntax - provides argument types alongside names
     */
    public MutableText getSyntax() {
        MutableText out = TextUtils.formattable("/").formatted(Formatting.YELLOW);
        out.append(TextUtils.formattable(this.alias).formatted(Formatting.YELLOW)).append(" ");
        for (VariableDefinition arg : this.argumentDefinitions.values()) {
            out.append(TextUtils.formattable("<").formatted(Formatting.WHITE))
                    .append(TextUtils.formattable(arg.name).formatted(Formatting.AQUA))
                    .append(TextUtils.formattable("> ").formatted(Formatting.WHITE));
        }
        return out;
    }

    /**
     * @return verbose command syntax - provides argument types alongside names
     */
    public MutableText getVerboseSyntax() {
        MutableText out = TextUtils.formattable("/").formatted(Formatting.YELLOW);
        out.append(TextUtils.formattable(this.alias).formatted(Formatting.YELLOW)).append(" ");
        for (VariableDefinition arg : this.argumentDefinitions.values()) {
            out.append(TextUtils.formattable("<").formatted(Formatting.GREEN))
                    .append(TextUtils.formattable(arg.name).formatted(Formatting.YELLOW))
                    .append(TextUtils.formattable(":").formatted(Formatting.GREEN))
                    .append(TextUtils.formattable(arg.typeName).formatted(Formatting.AQUA));
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
     * Generic range argument type for {@link AliasedCommand#ARGUMENT_TYPES}
     * @param opt should be a 2-element array of string inputs, parseable as T
     * @param clazz argument type; generic T is inferred from this
     * @param parse parsing function mapping opt->T
     * @param argumentType numeric range argument type provider
     * @return a matching argument type i.e {@link LongArgumentType#longArg(long, long)}
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

    /**
     * Renames an argument
     * @param source command source to send feedback to
     * @param name target argument
     * @param newName name to rename to
     * @return true if successful, false otherwise
     */
    public boolean renameArgument(ServerCommandSource source, String name, String newName) {
        if (this.argumentDefinitions.containsKey(newName)) {
            source.sendError(TextUtils.formattable("Argument \"" + newName + "\" already exists"));
            return false;
        }
        if (this.argumentDefinitions.containsKey(name)) {
            // too lazy to make a modified linked hashmap so just poll in reverse until it can replace the old entry
            LinkedHashMap<String, VariableDefinition> list = new LinkedHashMap<>();
            Map.Entry<String, VariableDefinition> tmp;
            while ((tmp = this.argumentDefinitions.pollLastEntry()) != null) {
                if (name.equals(tmp.getKey())) {
                    VariableDefinition v = tmp.getValue();
                    list.put(newName, new VariableDefinition(newName, v.typeName, v.args));
                    break;
                }
                else {
                    list.put(tmp.getKey(), tmp.getValue());
                }
            }
            this.argumentDefinitions.putAll(list.reversed());
            source.sendFeedback(() -> TextUtils.formattable("Renamed argument to ").append(TextUtils.formattable(newName)
                    .formatted(Formatting.GREEN)), false);
            this.refresh(source);
            return true;
        }
        source.sendError(TextUtils.formattable("Argument \"" + name + "\" not found"));
        return false;
    }

    /**
     * Provides this command's arguments; only used for {@link com.birblett.impl.command.AliasCommand#modifyListArguments(CommandContext, SuggestionsBuilder)}
     * @return command arguments, as an ordered collection
     */
    @SuppressWarnings("JavadocReference")
    public Collection<VariableDefinition> getArguments() {
        return this.argumentDefinitions.values();
    }

    /**
     * @return Whether the command has any arguments.
     */
    public boolean hasArguments() {
        return !this.argumentDefinitions.isEmpty();
    }

    /**
     * Deregisters and attempts to re-register this alias with the provided command source's server.
     * @param source command user; error messages sent if failed to re-register
     */
    public boolean refresh(ServerCommandSource source) {
        this.deregister(source.getServer(), false);
        return this.register(source);
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
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path, StandardOpenOption.TRUNCATE_EXISTING)) {
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
    public static boolean readFromFile(MinecraftServer server, Path path, boolean global) {
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
                            case "alias" -> {
                                alias = line.replaceFirst("(?i)Alias: *", "").strip();
                                if (AliasManager.ALIASES.containsKey(alias) && AliasManager.ALIASES.get(alias).global) {
                                    return false;
                                }
                            }
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
            new AliasedCommand(alias, permission, silent, server.getCommandManager().getDispatcher(), commands, arguments, global);
        }
        catch (IOException e) {
            TechnicalToolbox.warn("Something went wrong reading from file " + path);
        }
        return true;
    }

}