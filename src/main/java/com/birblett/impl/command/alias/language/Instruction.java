package com.birblett.impl.command.alias.language;

import com.birblett.TechnicalToolbox;
import com.birblett.accessor.command.CommandSourceModifier;
import com.birblett.accessor.command.delay.CommandScheduler;
import com.birblett.impl.command.alias.AliasedCommand;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;

import java.util.*;

/**
 * Basic interface for all instructions - returns -1 by default on execution, basically a no-op.
 */
public interface Instruction {

    default int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
        return -1;
    }

    /**
     * Holds a single command; does text replacement for variables on execution.
     *
     * @param command
     */
    record Command(String command) implements Instruction {

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            String cmd = this.command;
            for (String var : variables.keySet()) {
                cmd = cmd.replaceAll("\\{\\$" + var + "}", variables.get(var).value().toString());
            }
            cmd = cmd.replaceAll("\\{\\$@[a-zA-Z_][a-zA-Z0-9_]*}", "0");
            return aliasedCommand.executeCommand(context, cmd, false).success() ? -1 : -2;
        }

        @Override
        public String toString() {
            return this.command;
        }

    }

    /**
     * Is capable of assigning to variables and evaluating the value of expressions. Handles
     * order of operations and parentheses by converting to postfix and doing some preprocessing
     * during the compilation step, and interprets the postfix instructions via a stack.
     */
    class Let implements ExpressionParser, Instruction {

        public boolean valid = true;
        protected int type = 0;
        private final String assignVar;
        public String err = null;
        protected final Queue<Object> post = new LinkedList<>();

        public Let(String assignVar, String expr, List<LinkedHashMap<String, Variable.Definition>> vars) {
            String[] assn = assignVar.split(" ");
            if (assn.length == 2) {
                this.assignVar = assn[1];
            } else if (assn.length != 1) {
                this.assignVar = "";
                this.err = "expected 1-2 arguments for assignment, got " + assn.length;
                this.valid = false;
                return;
            } else {
                this.assignVar = assignVar;
            }
            if (!this.assignVar.matches("@?[a-zA-Z_][a-zA-Z0-9_]*")) {
                this.err = "invalid variable name " + this.assignVar;
                this.valid = false;
                return;
            }
            boolean newAssignment = true;
            LinkedHashMap<String, Variable.Definition> map = null;
            for (LinkedHashMap<String, Variable.Definition> varMap : vars) {
                if (varMap.containsKey(this.assignVar)) {
                    newAssignment = false;
                    map = varMap;
                    break;
                }
            }
            if (map != null) {
                this.type = AliasConstants.TYPE_MAP.getOrDefault(map.get(this.assignVar).type.clazz(), 4);
            }
            Integer forcedType = (!newAssignment && assn.length == 2) ? AliasConstants.TYPE_VALUE_MAP.get(assn[0]) : null;
            Integer type = this.parseExpression(expr, forcedType, vars, this.post);
            if (type == null) {
                this.valid = false;
                return;
            }
            this.type = type;
            String varType = AliasConstants.INV_VALUE_MAP.getOrDefault(this.type, "string");
            if (newAssignment) {
                if (this.assignVar.startsWith("@")) {
                    AliasedCommand.GLOBALS.put(this.assignVar, new Variable.Definition(this.assignVar, varType, new String[0]));
                } else {
                    vars.getLast().put(this.assignVar, new Variable.Definition(this.assignVar, varType, new String[0]));
                }
            } else {
                map.put(this.assignVar, new Variable.Definition(this.assignVar, varType, new String[0]));
            }
        }

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            Operator o = this.evaluate(aliasedCommand, context, this.post, variables);
            if (!variables.containsKey(this.assignVar)) {
                variables.put(this.assignVar, new Variable(new Variable.Definition(this.assignVar, AliasConstants.INV_VALUE_MAP
                        .getOrDefault(this.type, "string"), new String[0]), null));
            }
            if (o instanceof Operator.NumberOperator n) {
                variables.put(this.assignVar, new Variable(variables.get(this.assignVar).type(), n.getValue()));
            } else if (o instanceof Operator.StringOperator s) {
                variables.put(this.assignVar, new Variable(variables.get(this.assignVar).type(), s.str()));
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
    class Jump implements Instruction {

        public int jumpTo;

        public Jump(int jumpTo) {
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
     * The wait instruction; tells the interpreter to wait for a certain number of ticks.
     */

    class Wait implements Instruction, ExpressionParser {

        public int waitTicks;
        public boolean valid = true;
        public String err = null;

        public Wait(String waitTicks) {
            try {
                int i = Integer.parseInt(waitTicks);
                if (i <= 0) {
                    this.err = "wait time must be greater than 0";
                    this.valid = false;
                } else {
                    this.waitTicks = i;
                }
            } catch (NumberFormatException e) {
                this.err = "wait time must be an integer";
                this.valid = false;
            }
        }

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            aliasedCommand.wait = this.waitTicks;
            return -1;
        }

        @Override
        public String toString() {
            return "wait " + this.waitTicks;
        }

        @Override
        public void error(String s) {
            this.err = s;
        }

    }

    /**
     * Tests a condition; if it fails, it jumps to after the if statement ends. Jumping past
     * [elif/else/end] when successful is handled via {@link IfJump}.
     */
    class If extends Jump implements ExpressionParser {

        protected String name = "if";
        protected String cmp;
        private final Queue<Object> left = new LinkedList<>();
        private final Queue<Object> right = new LinkedList<>();
        public String err = null;
        public boolean valid = true;

        protected If() {
            super(-1);
        }

        public If(String expression, List<LinkedHashMap<String, Variable.Definition>> vars) {
            super(-1);
            this.err = this.validate(expression, vars);
            if (this.err != null) {
                this.valid = false;
            }
        }

        public String validate(String expression, List<LinkedHashMap<String, Variable.Definition>> vars) {
            String[] comparators = expression.split("( *[<>!=]= *| *[<>] *)", 2);
            if (comparators.length != 2) {
                return "must be be of format [" + this.name + " operator1 (==|>|>=|<|<=|!=) operator2]";
            }
            String cmp = expression.replace(comparators[0], "").replace(comparators[1], "").strip();
            if (cmp.length() == 1 && "<>".contains(cmp) || cmp.length() == 2 && cmp.matches("(<=|>=|!=|==)")) {
                this.cmp = cmp;
                Integer[] type = {0, 0};
                type[0] = this.parseExpression(comparators[0], null, vars, this.left);
                if (type[0] == null) {
                    return "invalid type for lhs expression";
                }
                type[1] = this.parseExpression(comparators[1], null, vars, this.right);
                if (type[1] == null) {
                    return "invalid type for rhs expression";
                }
                if (!Objects.equals(type[0], type[1]) && (type[0] == 4 || type[1] == 4) && !"==".equals(this.cmp) && !"!=".equals(this.cmp)) {
                    return "string type only supports comparison of equality";
                }
                return null;
            } else {
                return "invalid comparator \"" + cmp + "\"";
            }
        }

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            Operator l = this.evaluate(aliasedCommand, context, this.left, variables);
            Operator r = this.evaluate(aliasedCommand, context, this.right, variables);
            return l.compare(this.cmp, r) ? -1 : this.jumpTo;
        }

        @Override
        public String toString() {
            return this.name + " [" + this.left + this.cmp + this.right + "] else jmp " + this.jumpTo;
        }

        @Override
        public void error(String s) {
            this.err = s;
        }

    }


    /**
     * For all intents and purposes, an If Instruction, but evaluates the expression as a command and compares equality on the value.
     */
    class IfExec extends If {

        private final String commandString;
        private boolean inverted = false;

        public IfExec(String expression) {
            super();
            if (expression.startsWith("!")) {
                this.inverted = true;
                expression = expression.substring(1);
            }
            this.commandString = expression;
        }

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            boolean failSilent = aliasedCommand.getFailSilent();
            aliasedCommand.setFailSilent(false);
            boolean rval = aliasedCommand.executeCommand(context, this.commandString, true).success() ^ this.inverted;
            aliasedCommand.setFailSilent(failSilent);
            return rval ? -1 : this.jumpTo;
        }

        @Override
        public String toString() {
            return "ifexec [" + this.commandString + "] else jmp " + this.jumpTo;
        }

    }

    /**
     * A JumpInstruction with an extra depth field to tell the compiler not to overwrite other instructions when out of scope.
     */
    class IfJump extends Jump {

        protected final int depth;

        public IfJump(int jumpTo, int depth) {
            super(jumpTo);
            this.depth = depth;
        }

    }

    /**
     * For all intents and purposes, an If Instruction, but [elif/else/end] make some exceptions for this
     */
    class While extends If {

        public int startAddress;

        public While(int startAddress, String expression, List<LinkedHashMap<String, Variable.Definition>> vars) {
            super(expression, vars);
            this.startAddress = startAddress;
            this.name = "while";
        }

        protected While(int startAddress) {
            super();
            this.startAddress = startAddress;
            this.name = "while";
        }

    }


    /**
     * For all intents and purposes, an IfExec, but [elif/else/end] make some exceptions for this
     */
    class WhileExec extends While {

        private final String commandString;
        private boolean inverted = false;

        public WhileExec(int startAddress, String expression) {
            super(startAddress);
            this.name = "whileexec";
            if (expression.startsWith("!")) {
                this.inverted = true;
                expression = expression.substring(1);
            }
            this.commandString = expression;
        }

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            boolean failSilent = aliasedCommand.getFailSilent();
            aliasedCommand.setFailSilent(false);
            boolean rval = aliasedCommand.executeCommand(context, this.commandString, true).success() ^ this.inverted;
            aliasedCommand.setFailSilent(failSilent);
            return rval ? -1 : this.jumpTo;
        }

        @Override
        public String toString() {
            return "whileexec [" + this.commandString + "] else jmp " + this.jumpTo;
        }

    }

    /**
     * Sets a return value in the current context (if applicable) and tells the program to terminate.
     */
    class Return implements Instruction, ExpressionParser {

        public boolean valid = true;
        public String err = null;
        int inferredType = -1;
        private final Queue<Object> post = new LinkedList<>();

        public Return(String expr, List<LinkedHashMap<String, Variable.Definition>> vars) {
            if (!expr.isEmpty()) {
                this.inferredType = this.parseExpression(expr, null, vars, this.post);
            }
        }

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            if (this.inferredType >= 0) {
                Operator o = this.evaluate(aliasedCommand, context, this.post, variables);
                ((CommandSourceModifier) context.getSource()).technicalToolbox$setReturnValue(o);
            } else {
                ((CommandSourceModifier) context.getSource()).technicalToolbox$setReturnValue(null);
            }
            return -2;
        }

        @Override
        public void error(String s) {
            this.valid = false;
            this.err = s;
        }
    }

    /**
     * Fetches last return value, cast to a specific type. Numeric casts on numeric values will
     * cast as normal but will default to 0 if return type is string. Always defaults to 0 if
     * return value is null.
     */
    class Fetch implements Instruction, ExpressionParser {

        public boolean valid = true;
        public String err = null;
        private int type = 0;
        private String assignVar = null;

        public Fetch(String type, String var, List<LinkedHashMap<String, Variable.Definition>> vars) {
            if (!AliasConstants.TYPE_VALUE_MAP.containsKey(type)) {
                this.error("not a valid type: " + type);
            }
            this.assignVar = var;
            if (!this.assignVar.matches("@?[a-zA-Z_][a-zA-Z0-9_]*")) {
                this.err = "invalid variable name " + this.assignVar;
                this.valid = false;
                return;
            }
            this.type = AliasConstants.TYPE_VALUE_MAP.get(type);
            boolean newAssignment = true;
            Variable.Definition definition = new Variable.Definition(this.assignVar, AliasConstants.INV_VALUE_MAP.get(this.type),
                    new String[0]);
            for (LinkedHashMap<String, Variable.Definition> varMap : vars) {
                if (varMap.containsKey(this.assignVar)) {
                    newAssignment = false;
                    varMap.put(this.assignVar, definition);
                    break;
                }
            }
            if (newAssignment) {
                vars.getLast().put(this.assignVar, definition);
            }
        }

        @Override
        public int execute(AliasedCommand aliasedCommand, CommandContext<ServerCommandSource> context, LinkedHashMap<String, Variable> variables) {
            Operator o = ((CommandSourceModifier) context.getSource()).technicalToolbox$getReturnValue();
            if (o == null) {
                o = new Operator.NumberOperator(0);
            }
            Variable.Definition def = new Variable.Definition(this.assignVar, AliasConstants.INV_VALUE_MAP.get(this.type), new String[0]);
            variables.put(this.assignVar, new Variable(def, o.toType(this.type)));
            return -1;
        }

        @Override
        public void error(String s) {
            this.valid = false;
            this.err = s;
        }

    }

}
