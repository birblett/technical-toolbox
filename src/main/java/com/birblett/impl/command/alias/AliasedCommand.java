package com.birblett.impl.command.alias;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.config.ConfigOption;
import com.birblett.lib.command.CommandSourceModifier;
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

    private final String alias;
    private final List<String> commands = new ArrayList<>();
    private final List<Instruction> instructions = new ArrayList<>();
    private final LinkedHashMap<String, VariableDefinition> argumentDefinitions = new LinkedHashMap<>();
    private int permission;
    private boolean silent;
    private static final Pattern SAVED_ARGS = Pattern.compile("\\{\\$[^:]+(:[^}]+)?}");
    private static final Pattern STATEMENT = Pattern.compile("\\[\\[[^]]+]]");
    private static final Pattern STATEMENT_BEGIN = Pattern.compile("\\[\\[[^ \\]]*");
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

    private record Variable(VariableDefinition type, Object value) {}

    public interface Operator {

        Object getValue();
        Operator operation(String operator, Operator second);

    }

    private static class NumberOperator implements Operator {

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
                }
            }
            else if (other instanceof StringOperator str) {
                return new StringOperator(this.getValue().toString()).operation("+", str);
            }
            return null;
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

    private record StringOperator(String str) implements Operator {

        @Override
        public Object getValue() {
            return this.str;
        }

        @Override
        public StringOperator operation(String operator, Operator second) {
            if ("+".equals(operator)) {
                return new StringOperator(second.getValue() + this.str);
            }
            return null;
        }

    }

    private interface Instruction {

        int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables);

    }

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

    private static class EvaluateExpressionInstruction implements Instruction {

        private record Operator(String op, int precedence) {}
        private static final HashMap<String, Integer> PRECEDENCE = new HashMap<>();
        public static final HashMap<Class<?>, Integer> TYPE_MAP = new HashMap<>();
        public static final HashMap<Integer, String> INVERSE_TYPE_MAP = new HashMap<>();
        public static final Pattern EXPRESSION_TOKENIZER =
                Pattern.compile("((?<!\\\\)\".*?(?<!\\\\)\"|[0-9]+[.][0-9]+|[0-9]+|[()+\\-*/^]|[a-zA-Z]+)");

        static {
            PRECEDENCE.put("+", 0);
            PRECEDENCE.put("-", 0);
            PRECEDENCE.put("*", 1);
            PRECEDENCE.put("/", 1);
            PRECEDENCE.put("^", 2);
            TYPE_MAP.put(Integer.class, 0);
            TYPE_MAP.put(Long.class, 1);
            TYPE_MAP.put(Float.class, 2);
            TYPE_MAP.put(Double.class, 3);
            TYPE_MAP.put(String.class, 4);
            INVERSE_TYPE_MAP.put(0, "integer");
            INVERSE_TYPE_MAP.put(1, "long");
            INVERSE_TYPE_MAP.put(2, "float");
            INVERSE_TYPE_MAP.put(3, "double");
            INVERSE_TYPE_MAP.put(4, "string");
        }

        protected boolean valid = true;
        protected int type = 0;
        private final String assignVar;
        private final Queue<Object> post = new LinkedList<>();
        public String err = null;

        public EvaluateExpressionInstruction(String assignVar, String expr, List<LinkedHashMap<String, VariableDefinition>> vars) {
            String[] assn = assignVar.split(" ");
            if (assn.length == 2) {
                this.assignVar = assn[1];
            }
            else {
                this.assignVar = assignVar;
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
            Stack<Operator> stack = new Stack<>();
            boolean lastOperand = true;
            int depth = 0;
            Matcher m = EXPRESSION_TOKENIZER.matcher(expr);
            while (m.find()) {
                String token = m.group();
                if (token.startsWith("\"") && token.endsWith("\"")) {
                    if (newAssignment) {
                        this.type = 4;
                    }
                    else if (assn.length == 2 && "int|long|float|double".contains(assn[0])) {
                        this.err = "can't forcibly coerce string type to numerical value";
                        this.valid = false;
                        break;
                    }
                }
                switch (token) {
                    case " " -> {}
                    case "(" -> {
                        if (!lastOperand) {
                            this.valid = false;
                            break;
                        }
                        depth++;
                    }
                    case ")" -> {
                        if (lastOperand) {
                            this.valid = false;
                            break;
                        }
                        depth--;
                    }
                    case "+", "-", "*", "/", "^" -> {
                        if (lastOperand) {
                            this.valid = false;
                            break;
                        }
                        lastOperand = true;
                        int p = PRECEDENCE.get(token) + 3 * depth;
                        while (!stack.isEmpty() && stack.peek().precedence >= p) {
                            this.post.add(stack.pop().op);
                        }
                        stack.push(new Operator(token, p));
                    }
                    default -> {
                        if (!lastOperand) {
                            this.err = "token can't directly follow another token";
                            this.valid = false;
                            break;
                        }
                        NumberOperator num = NumberOperator.fromString(token);
                        if (num == null) {
                            if (token.startsWith("\"") && token.endsWith("\"")) {
                                this.type = 4;
                                this.post.add(new StringOperator(token.substring(1, token.length() - 1)));
                            }
                            else {
                                this.valid = false;
                                for (LinkedHashMap<String, VariableDefinition> varMap : vars) {
                                    if (varMap.containsKey(token)) {
                                        this.valid = true;
                                        this.type = Math.max(this.type, TYPE_MAP.getOrDefault(varMap.get(token).type.clazz, 4));
                                        this.post.add(token);
                                        break;
                                    }
                                }
                                if (!this.valid) {
                                    this.err = "no declaration/forward reference of variable \"" + token + "\"";
                                    break;
                                }
                            }
                        }
                        else {
                            if (newAssignment) {
                                Number n = (Number) num.getValue();
                                if (this.type == 0 && n.intValue() == num.getDoubleValue()) {
                                    this.type = 0;
                                }
                                else if (this.type <= 1 && n.longValue() == num.getDoubleValue()) {
                                    this.type = 1;
                                }
                                else if (this.type <= 2 && n.floatValue() == num.getDoubleValue()) {
                                    this.type = 2;
                                }
                                else {
                                    this.type = 3;
                                }
                            }
                            this.post.add(num);
                        }
                        lastOperand = false;
                    }
                }
            }
            if (lastOperand) {
                this.err = "expression contains operator without operand";
                this.valid = false;
            }
            if (this.valid) {
                while (!stack.isEmpty()) {
                    this.post.add(stack.pop().op);
                }
                if (depth != 0) {
                    this.err = "mismatched parentheses in expression";
                    this.valid = false;
                }
                if (this.valid) {
                    boolean hasString = false;
                    boolean hasNonAddition = false;
                    for (Object o : this.post) {
                        if (o instanceof String s && "-*/^".contains(s)) {
                            hasNonAddition = true;
                        }
                        else if (o instanceof StringOperator) {
                            this.type = 4;
                            hasString = true;
                        }
                    }
                    if (hasString && hasNonAddition) {
                        this.err = "string type only supports concatenation";
                        this.valid = false;
                    }
                    if (this.valid) {
                        String varType = assn.length == 2 && "int|long|float|long|string".contains(assn[0]) ? assn[0] :
                                INVERSE_TYPE_MAP.getOrDefault(this.type, "string");
                        if (newAssignment) {
                            vars.getLast().put(this.assignVar, new VariableDefinition(this.assignVar, varType, new String[0]));
                        }
                        else {
                            map.put(this.assignVar, new VariableDefinition(this.assignVar, varType, new String[0]));
                        }
                    }
                }
            }
        }

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            Queue<Object> postfix = new LinkedList<>(this.post);
            Stack<AliasedCommand.Operator> eval = new Stack<>();
            if (!postfix.isEmpty()) {
                if (postfix.peek() instanceof AliasedCommand.Operator o) {
                    eval.push(o);
                    postfix.poll();
                }
                while (!postfix.isEmpty()) {
                    Object o = postfix.poll();
                    if (o instanceof String tok) {
                        if ("+-*/^".contains(tok)) {
                            switch (tok) {
                                case "*", "+", "^" -> eval.push(eval.pop().operation(tok, eval.pop()));
                                case "/", "-" -> {
                                    AliasedCommand.Operator arg = eval.pop();
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
                    else if (o instanceof AliasedCommand.Operator op) {
                        eval.push(op);
                    }
                }
            }
            if (!variables.containsKey(this.assignVar)) {
                variables.put(this.assignVar, new Variable(new VariableDefinition(this.assignVar, INVERSE_TYPE_MAP
                        .getOrDefault(this.type, "string"), new String[0]), null));

            }
            if (eval.peek() instanceof NumberOperator n) {
                variables.put(this.assignVar, new Variable(variables.get(this.assignVar).type, n.getValue()));
            }
            else if (eval.peek() instanceof StringOperator s) {
                variables.put(this.assignVar, new Variable(variables.get(this.assignVar).type, s.str));
            }
            return -1;
        }

    }

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

    private static class IfInstruction extends JumpInstruction {

        protected String name = "if";
        protected String cmp;
        protected String left;
        private boolean[] ref = {false, false};
        protected String right;
        public boolean valid = true;
        public String err = null;
        public static final Pattern NON_VAR = Pattern.compile("((?<!\\\\)\".*?(?<!\\\\)\"|[0-9]+[.][0-9]+|[0-9]+)");

        public IfInstruction(String expression, List<LinkedHashMap<String, VariableDefinition>> vars) {
            super(-1);
            String[] comparators = expression.split(" *[<=>] *");
            if (comparators.length != 2) {
                this.err = "must be be of format [[" + this.name + " operator1 (>|>=|<|<=|==) operator2]]";
                this.valid = false;
            }
            else {
                String cmp = expression.replace(comparators[0], "").replace(comparators[1], "").strip();
                if (cmp.length() == 1 && "<=>".contains(cmp) || cmp.length() == 2 && cmp.matches("(<=|>=)")) {
                    this.cmp = cmp;
                    this.left = comparators[0];
                    this.right = comparators[1];
                    int i = 0;
                    for (String s : new String[]{this.left, this.right}) {
                        Matcher m = NON_VAR.matcher(s);
                        if (!m.find() || !m.group().equals(s)) {
                            this.ref[i] = true;
                            this.valid = false;
                            for (LinkedHashMap<String, VariableDefinition> varMap : vars) {
                                if (varMap.containsKey(s)) {
                                    this.valid = true;
                                    if (!cmp.equals("=") && !Number.class.isAssignableFrom(varMap.get(s).type.clazz)) {
                                        this.valid = false;
                                        this.err = "string types only support comparing equality";
                                        break;
                                    }
                                }
                            }
                            if (!this.valid) {
                                this.err = "no declaration/forward reference of variable \"" + s + "\"";
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
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            String l = this.left;
            String r = this.right;
            if (ref[0]) {
                l = variables.get(this.left).value.toString();
            }
            else if (this.left.startsWith("\"") && this.left.endsWith("\"")) {
                l = l.substring(1, l.length() - 1);
            }
            if (ref[1]) {
                r = variables.get(this.right).value.toString();
            }
            else if (this.right.startsWith("\"") && this.right.endsWith("\"")) {
                r = r.substring(1, r.length() - 1);
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

    private static class IfJumpInstruction extends JumpInstruction {

        protected final int depth;

        public IfJumpInstruction(int jumpTo, int depth) {
            super(jumpTo);
            this.depth = depth;
        }

    }

    private static class ElseInstruction extends JumpInstruction {

        public ElseInstruction(int jumpTo) {
            super(jumpTo);
        }

    }

    private static class WhileInstruction extends IfInstruction {

        protected int startAddress;

        public WhileInstruction(int startAddress, String expression, List<LinkedHashMap<String, VariableDefinition>> vars) {
            super(expression, vars);
            this.startAddress = startAddress;
            this.name = "while";
        }

    }

    private boolean compileError(int line, String err) {
        TechnicalToolbox.log("Failed to compile /{} - line {}: {}", this.alias, line + 1, err);
        this.status = "Line " + line + ": " + err;
        return false;
    }

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
                        String ctrl = m.group().replaceFirst("\\[\\[", "");
                        switch (ctrl) {
                            // handles assignment - handles expressions using either longs or doubles depending on which is necessary,
                            // then casts to matching type on assignment
                            case "assign" -> {
                                String[] instr = c.replace("[[assign", "").replace("]]", "").strip()
                                        .split("=", 2);
                                if (instr.length != 2 || instr[1].contains("=")) {
                                    return this.compileError(i, "assignment must be of form [[assign var = (expression)]]");
                                }
                                EvaluateExpressionInstruction e = new EvaluateExpressionInstruction(instr[0].strip(), instr[1], scope);
                                if (!e.valid) {
                                    return this.compileError(i, e.err);
                                }
                                this.instructions.add(e);
                            }
                            // basically tests a condition and if it fails it does a jump to the next elif/else
                            case "if" -> {
                                depth++;
                                scope.add(new LinkedHashMap<>());
                                String instr = c.replace("[[if", "").replace("]]", "").strip();
                                IfInstruction instruction = new IfInstruction(instr, scope);
                                if (!instruction.valid) {
                                    return this.compileError(i, instruction.err);
                                }
                                this.instructions.add(instruction);
                                controlFlowStack.add(instruction);
                            }
                            // sets previous if/elif conditional jump to the current address and creates an IfJump instruction, which will
                            // point to the next [[end]] block when that gets compiled
                            case "elif" -> {
                                if (controlFlowStack.peek() instanceof IfInstruction instruction) {
                                    instruction.jumpTo = ++address;
                                    controlFlowStack.pop();
                                    Instruction jumpInstruction = new IfJumpInstruction(-1, depth);
                                    this.instructions.add(jumpInstruction);
                                    controlFlowStack.add(jumpInstruction);
                                    String instr = c.replace("[[elif", "").replace("]]", "").strip();
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
                                    return this.compileError(i, "[[elif]] must follow an [[if/elif]]");
                                }
                            }
                            // strictly speaking this does not need an explicit instruction but i was lazy and didn't want to do extra
                            // validation for [[end]]
                            case "else" -> {
                                if (controlFlowStack.peek() instanceof IfInstruction instruction) {
                                    instruction.jumpTo = address + 1;
                                    controlFlowStack.pop();
                                    Instruction newInstruction = new ElseInstruction(address);
                                    this.instructions.add(newInstruction);
                                    controlFlowStack.add(newInstruction);
                                    scope.removeLast();
                                    scope.add(new LinkedHashMap<>());
                                }
                                else {
                                    return this.compileError(i, "{} - line {}: [[else]] must follow an [[if/elif]]");
                                }
                            }
                            // basically identical to if
                            case "while" -> {
                                depth++;
                                scope.add(new LinkedHashMap<>());
                                String instr = c.replace("[[while", "").replace("]]", "").strip();
                                WhileInstruction instruction = new WhileInstruction(address, instr, scope);
                                if (!instruction.valid) {
                                    return this.compileError(i, instruction.err);
                                }
                                this.instructions.add(instruction);
                                controlFlowStack.add(instruction);
                            }
                            // [[end]] handles all control flow so compilation depends on whatever happens to be on the stack
                            case "end" -> {
                                depth--;
                                scope.removeLast();
                                if (controlFlowStack.isEmpty()) {
                                    return this.compileError(i, "[[end]] does not enclose any control block");
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
                                return this.compileError(i, "invalid statement [[" + ctrl +"]]");
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
        /*
        TechnicalToolbox.log("/{} compiled", this.alias);
        for (int i = 0; i < this.instructions.size(); i++) {
            TechnicalToolbox.log("{}: {}", i, this.instructions.get(i));
        }
         */
        return true;
    }

    /**
     * Compiles the alias and if successful registers it as an executable command.
     * @param dispatcher dispatcher to register to
     * @return true if successful, false if compilation failed
     */
    public boolean register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Compile first, if compilation fails then it does nothing
        if (!this.compile()) {
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
                // arguments processed here
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
        if (!AliasManager.ALIASES.containsKey(this.alias)) {
            AliasManager.ALIASES.put(this.alias, this);
        }
        return true;
    }

    /**
     * Executes an alias from start to finish. Configured to interpret {@link AliasedCommand.Instruction}.
     * @param context command context
     */
    private int execute(CommandContext<ServerCommandSource> context) {
        LinkedHashMap<String, Variable> variableDefinitions = new LinkedHashMap<>();
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
        int i, c;
        List<AliasedCommand.Instruction> instructions = List.copyOf(this.instructions);
        // main loop for running instructions; opcode of -2 is error, -1 is donothing, >=0 is an instruction index to jump to
        for (i = c = 0; i < instructions.size() && c < 10000; i++, c++) {
            int out = instructions.get(i).execute(this, context, variableDefinitions);
            if (out == -2) {
                return 0;
            }
            else if (out >= 0) {
                i = out - 1;
            }
        }
        if (i < instructions.size()) {
            context.getSource().sendError(TextUtils.formattable("Exceeded the instruction limit of " + 10000));
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

    /**
     * Adds a command to the end of the current alias script and automatically updates argument count.
     * @param command full command to add.
     */
    public void addCommand(String command) {
        this.commands.add(command);
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
        context.getSource().sendFeedback(this::getVerboseSyntax, false);
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