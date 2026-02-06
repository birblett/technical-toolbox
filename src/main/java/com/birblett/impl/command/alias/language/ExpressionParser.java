package com.birblett.impl.command.alias.language;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.command.alias.AliasedCommand;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;

import java.util.*;
import java.util.regex.Matcher;

/**
 * Contains methods and values for parsing expressions i.e. for if/let/return statements
 */
public interface ExpressionParser {

    record ExpressionOperator(String op, int precedence) {
    }

    default Integer parseExpression(String expr, Integer type, List<LinkedHashMap<String, Variable.Definition>> vars, Queue<Object> post) {
        int inferredType = type != null ? type : 0;
        Stack<ExpressionOperator> stack = new Stack<>();
        Matcher m = AliasConstants.TOKEN.matcher(expr);
        boolean lastOperand = true;
        int depth = 0;
        while (m.find()) {
            String token = m.group();
            if (token.startsWith("\"") && token.endsWith("\"")) {
                if (type == null) {
                    inferredType = 4;
                } else if (type < 4) {
                    this.error("can't forcibly coerce string type to numerical value");
                    return null;
                }
            }
            switch (token) {
                case " " -> {
                }
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
                    int p = AliasConstants.PRECEDENCE.get(token) + 3 * depth;
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
                    if (token.startsWith("(eval ")) {
                        Matcher m2 = AliasConstants.VAR.matcher(expr);
                        TechnicalToolbox.log("par");
                        while (m2.find()) {
                            String tok = m2.group();
                            boolean valid = false;
                            for (LinkedHashMap<String, Variable.Definition> varMap : vars) {
                                if (varMap.containsKey(tok.substring(2, tok.length() - 1))) {
                                    valid = true;
                                    break;
                                }
                            }
                            if (!valid) {
                                this.error("no declaration/forward reference of variable \"" + token + "\"");
                                return null;
                            }
                        }
                        post.add(new Operator.EvalOperator(token.substring(6, token.length() - 1)));
                        inferredType = Math.max(1, inferredType);
                    } else {
                        Operator.NumberOperator num = Operator.NumberOperator.fromString(token);
                        if (num == null) {
                            if (token.startsWith("\"") && token.endsWith("\"")) {
                                inferredType = 4;
                                post.add(new Operator.StringOperator(token.substring(1, token.length() - 1)));
                            } else {
                                boolean valid = false;
                                for (LinkedHashMap<String, Variable.Definition> varMap : vars) {
                                    if (varMap.containsKey(token)) {
                                        valid = true;
                                        inferredType = Math.max(inferredType, AliasConstants.TYPE_MAP.getOrDefault(varMap.get(token).type.clazz(), 4));
                                        post.add(token);
                                        break;
                                    }
                                }
                                if (!valid && token.startsWith("@")) {
                                    post.add(token);
                                    valid = true;
                                }
                                if (!valid) {
                                    this.error("no declaration/forward reference of variable \"" + token + "\"");
                                    return null;
                                }
                            }
                        } else {
                            if (type == null) {
                                Number n = (Number) num.getValue();
                                if (token.endsWith("f")) {
                                    inferredType = Math.max(2, inferredType);
                                } else {
                                    if (inferredType != 0 || n.intValue() != num.getDoubleValue()) {
                                        if (inferredType <= 1 && n.longValue() == num.getDoubleValue()) {
                                            inferredType = 1;
                                        } else {
                                            inferredType = Math.max(3, inferredType);
                                        }
                                    }
                                }
                            }
                            post.add(num);
                        }
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
            } else if (o instanceof Operator.StringOperator) {
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

    default Operator evaluate(AliasedCommand command, CommandContext<ServerCommandSource> context, Queue<Object> post, LinkedHashMap<String, Variable> variables) {
        Queue<Object> postfix = new LinkedList<>(post);
        Stack<Operator> eval = new Stack<>();
        if (!postfix.isEmpty()) {
            while (!postfix.isEmpty()) {
                Object o = postfix.poll();
                if (o instanceof Operator.EvalOperator(String expr)) {
                    String expr2 = getVarValue(variables, expr);
                    AliasedCommand.CommandResult v = command.executeCommand(context, expr2, true);
                    eval.push(new Operator.NumberOperator(v.success() ? v.value() : 0));
                } if (o instanceof String tok) {
                    if ("+-*/^%".contains(tok)) {
                        switch (tok) {
                            case "*", "+", "^", "%" -> eval.push(eval.pop().operation(tok, eval.pop()));
                            case "/", "-" -> {
                                Operator arg = eval.pop();
                                eval.push(eval.pop().operation(tok, arg));
                            }
                        }
                    } else {
                        Variable v = variables.get(tok);
                        if (v == null && tok.startsWith("@")) {
                            eval.push(new Operator.NumberOperator(0));
                        } else {
                            if (Number.class.isAssignableFrom(v.type().type.clazz())) {
                                eval.push(new Operator.NumberOperator((Number) v.value()));
                            } else {
                                eval.push(new Operator.StringOperator(v.value().toString()));
                            }
                        }
                    }
                } else if (o instanceof Operator op) {
                    eval.push(op);
                }
            }
        }
        return eval.peek();
    }

    private static String getVarValue(LinkedHashMap<String, Variable> variables, String expr) {
        Matcher m2 = AliasConstants.VAR.matcher(expr);
        return m2.replaceAll((match) -> {
            String tok = match.group();
            Variable v = variables.get(tok.substring(2, tok.length() - 1));
            if (v == null) {
                return tok.startsWith("${@") ? "0" : tok;
            } else {
                return v.value().toString();
            }
        });
    }

    void error(String s);

}
