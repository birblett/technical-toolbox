package com.birblett.impl.command.alias.language;

import com.mojang.brigadier.arguments.*;

import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

public class AliasConstants {

    public static final HashMap<String, Variable.Entry<?>> ARGUMENT_TYPES = new HashMap<>();
    public static final HashMap<String, Integer> PRECEDENCE = new HashMap<>();
    public static final HashMap<Class<?>, Integer> TYPE_MAP = new HashMap<>();
    public static final HashMap<String, Integer> TYPE_VALUE_MAP = new HashMap<>();
    public static final HashMap<Integer, String> INV_VALUE_MAP = new HashMap<>();
    public static final Pattern TOKEN = Pattern.compile("((?<!\\\\)\".*?(?<!\\\\)\"|[0-9]+[.][0-9]+[fF]?|[0-9]+[fF]?|[()+\\-%*/^]|[a-zA-Z_][a-zA-Z0-9_]*)");

    static {
        ARGUMENT_TYPES.put("int", new Variable.Entry<>(0, opt -> IntegerArgumentType.integer(), Integer.class));
        ARGUMENT_TYPES.put("int_range", new Variable.Entry<>(2, opt -> rangeArgumentType(opt, Integer.class, Integer::parseInt,
                IntegerArgumentType::integer), Integer.class));
        ARGUMENT_TYPES.put("long", new Variable.Entry<>(0, opt -> LongArgumentType.longArg(), Long.class));
        ARGUMENT_TYPES.put("long_range", new Variable.Entry<>(2, opt -> rangeArgumentType(opt, Long.class, Long::parseLong,
                LongArgumentType::longArg), Long.class));
        ARGUMENT_TYPES.put("float", new Variable.Entry<>(0, opt -> FloatArgumentType.floatArg(), Float.class));
        ARGUMENT_TYPES.put("float_range", new Variable.Entry<>(2, opt -> rangeArgumentType(opt, Float.class, Float::parseFloat,
                FloatArgumentType::floatArg), Float.class));
        ARGUMENT_TYPES.put("double", new Variable.Entry<>(0, opt -> DoubleArgumentType.doubleArg(), Double.class));
        ARGUMENT_TYPES.put("double_range", new Variable.Entry<>(2, opt -> rangeArgumentType(opt, Double.class,
                Double::parseDouble, DoubleArgumentType::doubleArg), Double.class));
        ARGUMENT_TYPES.put("boolean", new Variable.Entry<>(0, opt -> BoolArgumentType.bool(), Boolean.class));
        ARGUMENT_TYPES.put("word", new Variable.Entry<>(0, opt -> StringArgumentType.word(), String.class));
        ARGUMENT_TYPES.put("string", new Variable.Entry<>(0, opt -> StringArgumentType.string(), String.class));
        ARGUMENT_TYPES.put("regex", new Variable.Entry<>(1, opt -> StringArgumentType.string(), String.class));
        ARGUMENT_TYPES.put("selection", new Variable.Entry<>(-1, opt -> StringArgumentType.string(), String.class));
        PRECEDENCE.put("+", 0);
        PRECEDENCE.put("-", 0);
        PRECEDENCE.put("*", 1);
        PRECEDENCE.put("/", 1);
        PRECEDENCE.put("%", 1);
        PRECEDENCE.put("^", 2);
        TYPE_MAP.put(Integer.class, 0);
        TYPE_MAP.put(Long.class, 1);
        TYPE_MAP.put(Float.class, 2);
        TYPE_MAP.put(Double.class, 3);
        TYPE_MAP.put(String.class, 4);
        TYPE_VALUE_MAP.put("int", 0);
        TYPE_VALUE_MAP.put("long", 1);
        TYPE_VALUE_MAP.put("float", 2);
        TYPE_VALUE_MAP.put("double", 3);
        TYPE_VALUE_MAP.put("string", 4);
        INV_VALUE_MAP.put(0, "int");
        INV_VALUE_MAP.put(1, "long");
        INV_VALUE_MAP.put(2, "float");
        INV_VALUE_MAP.put(3, "double");
        INV_VALUE_MAP.put(4, "string");
    }

    /**
     * Generic range argument type for {@link AliasConstants#ARGUMENT_TYPES}
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

}
