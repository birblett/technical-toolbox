package com.birblett.impl.command.alias;

import com.birblett.TechnicalToolbox;
import com.birblett.accessor.command.CommandSourceModifier;
import com.birblett.accessor.command.delay.AliasedCommandSource;
import com.birblett.impl.command.alias.language.AliasConstants;
import com.birblett.impl.command.alias.language.Instruction;
import com.birblett.impl.command.alias.language.Variable;
import com.birblett.impl.config.ConfigOptions;
import com.birblett.util.ServerUtil;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
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
    private final LinkedHashMap<String, Variable.Definition> argumentDefinitions = new LinkedHashMap<>();
    private int permission;
    private boolean silent;
    public final boolean global;
    private static final Pattern SAVED_ARGS = Pattern.compile("\\{\\$[^:]+(:[^}]+)?}");
    private static final Pattern STATEMENT = Pattern.compile("\\[.*]");
    private static final Pattern STATEMENT_BEGIN = Pattern.compile("\\[[^ ]+");
    public String status = null;

    public AliasedCommand(String alias, String command, CommandDispatcher<ServerCommandSource> dispatcher) {
        this.global = false;
        this.alias = alias;
        this.commands.add(command);
        this.permission = ConfigOptions.ALIAS_DEFAULT_PERMISSION.val();
        this.silent = ConfigOptions.ALIAS_DEFAULT_SILENT.val();
        AliasManager.ALIASES.put(this.alias, this);
        this.register(dispatcher);
    }

    private AliasedCommand(String alias, int permission, boolean silent, Collection<String> commands, Collection<Variable.Definition> arguments, boolean global) {
        this.global = global;
        this.alias = alias;
        this.commands.addAll(commands);
        List<String> args = new ArrayList<>();
        for (Variable.Definition var : arguments) {
            if (var.name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                this.argumentDefinitions.put(var.name, var);
            }
            else {
                args.add(var.name);
            }
        }
        if (!args.isEmpty()) {
            TechnicalToolbox.error("{}: couldn't parse argument(s) {}", alias, args);
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
     * Attempts to compile the currently stored alias; control flow/assignment are formatted as
     * [statement], everything else that does not match the regex will be interpreted as a command
     * @return false if it fails to compile
     */
    private boolean compile() {
        this.instructions.clear();
        Stack<Instruction> controlFlowStack = new Stack<>();
        List<LinkedHashMap<String, Variable.Definition>> scope = new ArrayList<>();
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
                                Instruction.Let e = new Instruction.Let(instr[0].strip(), instr[1], scope);
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
                                Instruction.If instruction = new Instruction.If(instr, scope);
                                if (!instruction.valid) {
                                    return this.compileError(i, instruction.err);
                                }
                                this.instructions.add(instruction);
                                controlFlowStack.add(instruction);
                            }
                            // sets previous if/elif conditional jump to the current address and creates an IfJump instruction, which will
                            // point to the next [end] block when that gets compiled
                            case "elif" -> {
                                if (!controlFlowStack.isEmpty() && controlFlowStack.peek() instanceof Instruction.If instruction &&
                                        !(instruction instanceof Instruction.While)) {
                                    instruction.jumpTo = ++address;
                                    controlFlowStack.pop();
                                    Instruction jumpInstruction = new Instruction.IfJump(-1, depth);
                                    this.instructions.add(jumpInstruction);
                                    controlFlowStack.add(jumpInstruction);
                                    String instr =  c.substring(1, c.length() - 1).replaceFirst("elif", "").strip();
                                    Instruction.If newInstruction = new Instruction.If(instr, scope);
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
                                if (!controlFlowStack.isEmpty() && controlFlowStack.peek() instanceof Instruction.If instruction &&
                                        !(instruction instanceof Instruction.While)) {
                                    instruction.jumpTo = address + 1;
                                    controlFlowStack.pop();
                                    Instruction newInstruction = new Instruction.Jump(address);
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
                                Instruction.While instruction = new Instruction.While(address, instr, scope);
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
                                else if (controlFlowStack.peek() instanceof Instruction.While instruction) {
                                    this.instructions.add(new Instruction.Jump(instruction.startAddress));
                                    instruction.jumpTo = address + 1;
                                    controlFlowStack.pop();
                                }
                                else if (controlFlowStack.peek() instanceof Instruction.Jump instruction) {
                                    instruction.jumpTo = address--;
                                    controlFlowStack.pop();
                                    // processes all the previous if/elif/else chains, so they correctly skip over elif/else after if
                                    // their condition passes
                                    while (!controlFlowStack.isEmpty() && controlFlowStack.peek() instanceof Instruction.IfJump instruction1) {
                                        instruction1.jumpTo = address + 1;
                                        controlFlowStack.pop();
                                    }
                                }
                                else {
                                    // is this even reachable i have no idea
                                    return false;
                                }
                            }
                            // [return] causes the program to terminate immediately and if specified can return a value accessed via [fetch]
                            case "return" -> {
                                String instr =  c.substring(1, c.length() - 1).replaceFirst("return", "").strip();
                                Instruction.Return instruction = new Instruction.Return(instr, scope);
                                if (!instruction.valid) {
                                    return this.compileError(i, instruction.err);
                                }
                                this.instructions.add(instruction);
                            }
                            // [fetch] retrieves the last return value in scope. there is no type inference for return values so it must be
                            // cast. attempting to cast a string as any number will set it to 0.
                            case "fetch" -> {
                                String[] instr =  c.substring(1, c.length() - 1).replaceFirst("fetch", "").strip()
                                        .split(" ");
                                if (instr.length != 2) {
                                    return this.compileError(i, "fetch should be of form [fetch type <var>]");
                                }
                                Instruction.Fetch instruction = new Instruction.Fetch(instr[0], instr[1], scope);
                                if (!instruction.valid) {
                                    return this.compileError(i, instruction.err);
                                }
                                this.instructions.add(instruction);
                            }
                            default -> {
                                return this.compileError(i, "invalid statement [" + ctrl +"]");
                            }
                        }
                    }
                }
                else {
                    this.instructions.add(new Instruction.Command(cmd));
                }
                address++;
            }
        }
        while (!controlFlowStack.isEmpty()) {
            scope.removeLast();
            if (controlFlowStack.peek() instanceof Instruction.While instruction) {
                this.instructions.add(new Instruction.Jump(instruction.startAddress));
                instruction.jumpTo = address + 1;
                controlFlowStack.pop();
            }
            else if (controlFlowStack.peek() instanceof Instruction.Jump instruction) {
                instruction.jumpTo = address--;
                controlFlowStack.pop();
                while (!controlFlowStack.isEmpty() && controlFlowStack.peek() instanceof Instruction.IfJump instruction1) {
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
     * Logs an error and stores an error message.
     * @param line line number
     * @param err specific error mesasge
     * @return always false, inlined when compiler fails
     */
    private boolean compileError(int line, String err) {
        TechnicalToolbox.error("Failed to compile /{} - line {}: {}", this.alias, line + 1, err);
        this.status = "Line " + line + ": " + err;
        return false;
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
            Variable.Definition[] vars = this.argumentDefinitions.values().toArray(new Variable.Definition[0]);
            for (int i = vars.length - 1; i >= 0; i--) {
                Variable.Definition def = vars[i];
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
     * Executes an alias from start to finish. Configured to interpret {@link Instruction}.
     * @param context command context
     */
    private int execute(CommandContext<ServerCommandSource> context) {
        LinkedHashMap<String, Variable> variableDefinitions = new LinkedHashMap<>();
        List<Instruction> instructions = List.copyOf(this.instructions);
        AliasedCommandSource source = (AliasedCommandSource) context.getSource();
        source.technicalToolbox$AddToRecursionDepth(1);
        // load arguments locally
        for (Variable.Definition var : this.argumentDefinitions.values()) {
            if ("selection".equals(var.typeName)) {
                String arg = ((CommandSourceModifier) context.getSource()).technicalToolbox$getSelectorArgument(var.name);
                variableDefinitions.put(var.name, new Variable(var, arg));
            }
            else {
                Object arg = context.getArgument(var.name, var.type.clazz());
                variableDefinitions.put(var.name, new Variable(var, arg));
            }
        }
        int i;
        // main loop for running instructions; opcode of -2 is return, -1 is donothing, >=0 is an instruction index to jump to
        for (i = 0; i < instructions.size() && (ConfigOptions.ALIAS_INSTRUCTION_LIMIT.val() == -1 ||
                source.technicalToolbox$getInstructionCount() < ConfigOptions.ALIAS_INSTRUCTION_LIMIT.val()) &&
                source.technicalToolbox$getRecursionCount() < ConfigOptions.ALIAS_MAX_RECURSION_DEPTH.val(); i++) {
            source.technicalToolbox$AddToInstructionCount(1);
            int out = instructions.get(i).execute(this, context, variableDefinitions);
            if (out == -2) {
                return 0;
            }
            else if (out >= 0) {
                i = out - 1;
            }
        }
        if (source.technicalToolbox$getRecursionCount() >= ConfigOptions.ALIAS_MAX_RECURSION_DEPTH.val()) {
            if (source.technicalToolbox$getRecursionCount() == ConfigOptions.ALIAS_MAX_RECURSION_DEPTH.val()) {
                context.getSource().sendError(TextUtils.formattable("Exceeded the max recursion depth of " +
                        ConfigOptions.ALIAS_MAX_RECURSION_DEPTH.val()));
                source.technicalToolbox$AddToRecursionDepth(1);
            }
            return 0;
        }
        if (i < instructions.size()) {
            context.getSource().sendError(TextUtils.formattable("Exceeded the instruction limit of " +
                    ConfigOptions.ALIAS_INSTRUCTION_LIMIT.val()));
            return 0;
        }
        if (source.technicalToolbox$getRecursionCount() < ConfigOptions.ALIAS_MAX_RECURSION_DEPTH.val()) {
            source.technicalToolbox$AddToRecursionDepth(-1);
        }
        return 1;
    }

    /**
     * Executes command on server with command permission level override enabled
     * @param context command context
     * @param command command to execute
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean executeCommand(CommandContext<ServerCommandSource> context, String command) {
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
        for (Variable.Definition arg : this.argumentDefinitions.values()) {
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
        for (Variable.Definition arg : this.argumentDefinitions.values()) {
            out.append(TextUtils.formattable("<").formatted(Formatting.GREEN))
                    .append(TextUtils.formattable(arg.name).formatted(Formatting.YELLOW))
                    .append(TextUtils.formattable(":").formatted(Formatting.GREEN))
                    .append(TextUtils.formattable(arg.typeName).formatted(Formatting.AQUA));
            if (arg.type.argc() != 0) {
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
        if (!AliasConstants.ARGUMENT_TYPES.containsKey(argType)) {
            source.sendError(TextUtils.formattable("Not a valid argument type: " + argType));
            return false;
        }
        this.argumentDefinitions.put(name, new Variable.Definition(name, argType, args));
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
            LinkedHashMap<String, Variable.Definition> list = new LinkedHashMap<>();
            Map.Entry<String, Variable.Definition> tmp;
            while ((tmp = this.argumentDefinitions.pollLastEntry()) != null) {
                if (name.equals(tmp.getKey())) {
                    Variable.Definition v = tmp.getValue();
                    list.put(newName, new Variable.Definition(newName, v.typeName, v.args));
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
     * Provides this command's arguments; only used for {@link AliasCommand#modifyListArguments(CommandContext, SuggestionsBuilder)}
     * @return command arguments, as an ordered collection
     */
    @SuppressWarnings("JavadocReference")
    public Collection<Variable.Definition> getArguments() {
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
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path)) {
            bufferedWriter.write("Alias: " + this.alias + "\n");
            if (this.permission != ConfigOptions.ALIAS_DEFAULT_PERMISSION.val()) {
                bufferedWriter.write("Permission level: " + this.permission + "\n");
            }
            if (this.silent != (ConfigOptions.ALIAS_DEFAULT_SILENT.val())) {
                bufferedWriter.write("Silent: \"" + this.silent + "\"\n");
            }
            if (!this.argumentDefinitions.isEmpty()) {
                bufferedWriter.write("Arguments:");
                for (Variable.Definition var : this.argumentDefinitions.values()) {
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
     * @param path path to read from
     * @return whether alias was successfully restored or not; outputs errors if failed
     */
    public static boolean readFromFile(Path path, boolean global) {
        try (BufferedReader bufferedReader = Files.newBufferedReader(path)) {
            boolean readingCommandState = false, silent = ConfigOptions.ALIAS_DEFAULT_SILENT.val();
            String line, alias = null;
            int permission = ConfigOptions.ALIAS_DEFAULT_PERMISSION.val();
            List<String> commands = new ArrayList<>();
            List<Variable.Definition> arguments = new ArrayList<>();
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
                                    TechnicalToolbox.error(path + ": Couldn't parse \"" + tmp + "\" as int");
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
                                        arguments.add(new Variable.Definition(name, "string", new String[0]));
                                    }
                                    else {
                                        temp = temp[1].split("\\|");
                                        arguments.add(new Variable.Definition(name, temp[0], temp.length > 1 ? temp[1].split(",") :
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
                TechnicalToolbox.error(path + ": Alias not specified in file");
                return false;
            }
            if (commands.isEmpty()) {
                TechnicalToolbox.error(path + ": Missing script body");
                return false;
            }
            new AliasedCommand(alias, permission, silent, commands, arguments, global);
        }
        catch (IOException e) {
            TechnicalToolbox.warn("Something went wrong reading from file " + path);
        }
        return true;
    }

}