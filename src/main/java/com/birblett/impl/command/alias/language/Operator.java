package com.birblett.impl.command.alias.language;

import com.birblett.TechnicalToolbox;

/**
 * The basic interface for operators in expressions - essentially a data container.
 * getValue returns raw data, operation specifies how it interfaces with other
 * Operators.
 */
public interface Operator {

    Object getValue();
    Operator operation(String operator, Operator other);
    boolean compare(String comparator, Operator other);
    Object toType(int type);

    /**
     * Supports operations between two numbers, or a number and a string. Internal calculations
     * handled via long and double values where necessary.
     */
    class NumberOperator implements Operator {

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
            return this.isLong ? this.longVal : this.doubleVal;
        }

        @Override
        public Object getValue() {
            return this.isLong ? (Object) this.longVal : (Object) this.doubleVal;
        }

        public Operator operation(String operator, Operator other) {
            if (other instanceof NumberOperator second) {
                switch (operator) {
                    case "+" -> {
                        return new NumberOperator((this.isLong && second.isLong) ? (Number) (this.longVal + second.longVal) : (Number)
                                (this.getDoubleValue() + second.getDoubleValue()));
                    }
                    case "-" -> {
                        return new NumberOperator((this.isLong && second.isLong) ? (Number) (this.longVal - second.longVal) : (Number)
                                (this.getDoubleValue() - second.getDoubleValue()));
                    }
                    case "*" -> {
                        return new NumberOperator((this.isLong && second.isLong) ? (Number) (this.longVal * second.longVal) : (Number)
                                (this.getDoubleValue() * second.getDoubleValue()));
                    }
                    case "/" -> {
                        return new NumberOperator((this.isLong && second.isLong) ? (Number) (this.longVal / second.longVal) : (Number)
                                (this.getDoubleValue() / second.getDoubleValue()));
                    }
                    case "^" -> {
                        return new NumberOperator((this.isLong && second.isLong) ? Math.pow(second.longVal, this.longVal) :
                                Math.pow(second.getDoubleValue(), this.getDoubleValue()));
                    }
                    case "%" -> {
                        return new NumberOperator((this.isLong && second.isLong) ? (Number) (this.longVal % second.longVal) : (Number)
                                (this.getDoubleValue() % second.getDoubleValue()));
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

        @Override
        public Object toType(int type) {
            switch (type) {
                case 0 -> {
                    return ((Number) this.getValue()).intValue();
                }
                case 1 -> {
                    return ((Number) this.getValue()).longValue();
                }
                case 2 -> {
                    return ((Number) this.getValue()).floatValue();
                }
                case 3 -> {
                    return ((Number) this.getValue()).doubleValue();
                }
            }
            return String.valueOf(this.isLong ? this.longVal : this.getDoubleValue());
        }

        @Override
        public String toString() {
            return (this.isLong ? "long" : "double") + ": " + this.getValue();
        }

    }

    record StringOperator(String str) implements Operator {

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

        @Override
        public Object toType(int type) {
            if (type < 4) {
                return 0;
            }
            return this.str;
        }

    }

}
