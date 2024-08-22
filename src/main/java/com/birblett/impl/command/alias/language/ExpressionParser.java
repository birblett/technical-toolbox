package com.birblett.impl.command.alias.language;

import java.util.*;
import java.util.regex.Matcher;

/**
 * Contains methods and values for parsing expressions i.e. for if/let/return statements
 */
public interface ExpressionParser {

    record ExpressionOperator(String op, int precedence) {}

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
                            if (!valid) {
                                this.error("no declaration/forward reference of variable \"" + token + "\"");
                                return null;
                            }
                        }
                    } else {
                        if (type == null) {
                            Number n = (Number) num.getValue();
                            if (token.endsWith("f")) {
                                inferredType = 2;
                            } else {
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
                    } else {
                        Variable v = variables.get(tok);
                        if (Number.class.isAssignableFrom(v.type().type.clazz())) {
                            eval.push(new Operator.NumberOperator((Number) v.value()));
                        } else {
                            eval.push(new Operator.StringOperator(v.value().toString()));
                        }
                    }
                } else if (o instanceof Operator op) {
                    eval.push(op);
                }
            }
        }
        return eval.peek();
    }

    void error(String s);

}
