package com.birblett.impl.alias;

import com.birblett.TechnicalToolbox;
import com.birblett.lib.command.CommandSourceModifier;
import com.birblett.util.ServerUtil;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
 */
public class AliasedCommand {

    private final String alias;
    private final List<String> commands = new ArrayList<>();
    private int argCount;
    private int permission;
    private final Set<String> args = new LinkedHashSet<>();
    private static final Pattern ARG = Pattern.compile("\\{\\$[^{$}]+}");
    private String separator = " ";

    public AliasedCommand(String alias, String command, int permission, CommandDispatcher<ServerCommandSource> dispatcher) {
        this.alias = alias;
        this.commands.add(command);
        this.permission = permission;
        this.updateArgCount();
        this.register(dispatcher);
    }

    private AliasedCommand(String alias, int permission, String separator, CommandDispatcher<ServerCommandSource> dispatcher, Collection<String> commands) {
        this.alias = alias;
        this.commands.addAll(commands);
        this.permission = permission;
        this.separator = separator;
        this.updateArgCount();
        this.register(dispatcher);
    }

    public void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        AliasManager.ALIASES.put(this.alias, this);
        dispatcher.register((CommandManager.literal(this.alias)
                .requires(source -> source.hasPermissionLevel(this.getPermission())))
                // Execution if args provided - no suggestion if no args required
                .then(CommandManager.argument("arguments", StringArgumentType.greedyString())
                        .requires(source -> this.argCount > 0)
                        .executes(context -> {
                            String[] args = context.getArgument("arguments", String.class).split(" *" +
                                    this.separator + " *");
                            boolean bl = args.length == this.args.size();
                            if (bl) {
                                this.execute(context, args);
                            }
                            else {
                                MutableText text = TextUtils.formattable("Alias ").append(TextUtils.formattable(this.alias)
                                        .formatted(Formatting.GREEN)).append(TextUtils.formattable(" requires " +
                                        this.args.size() + " arguments: "));
                                text.append(this.getCommaSeparateArgs());
                                context.getSource().sendMessage(text);
                            }
                            return 1;
                        }))
                // Provides arguments if present
                .then(CommandManager.literal("help")
                        .requires(source -> this.argCount > 0)
                        .executes(context -> {
                            MutableText text;
                            if (this.argCount > 1) {
                                text = TextUtils.formattable("Requires " + this.argCount + " arguments (\"")
                                        .append(TextUtils.formattable(this.separator).formatted(Formatting.YELLOW))
                                        .append("\" separated): ");
                            }
                            else {
                                text = TextUtils.formattable("Requires " + this.argCount + " arguments: ");
                            }
                            text.append(this.getCommaSeparateArgs());
                            context.getSource().sendFeedback(() -> text, false);
                            return 1;
                        }))
                // Execution if no args provided
                .executes(context -> {
                    if (this.argCount > 0) {
                        MutableText text = TextUtils.formattable("Alias ").append(TextUtils.formattable(this.alias)
                                .formatted(Formatting.GREEN)).append(TextUtils.formattable(" requires " +
                                this.args.size() + " arguments: "));
                        text.append(this.getCommaSeparateArgs());
                        context.getSource().sendFeedback(() -> text, false);
                        return 0;
                    }
                    this.execute(context);
                    return 1;
                }));
    }

    public int getArgCount() {
        return this.argCount;
    }

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

    public void setPermission(int permission) {
        this.permission = permission;
    }

    public int getPermission() {
        return this.permission;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public String getSeparator() {
        return this.separator;
    }

    public List<String> getCommands() {
        return this.commands;
    }

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

    public void addCommand(String command) {
        this.commands.add(command);
        this.updateArgCount();
    }

    public MutableText removeCommand(int line) {
        if (this.commands.size() <= 1) {
            return TextUtils.formattable("Can't remove the last line in an alias");
        }
        if (line < 1 || line - 1 > this.commands.size()) {
            return TextUtils.formattable("Line index " + line + " out of bounds");
        }
        this.commands.remove(line - 1);
        this.updateArgCount();
        return null;
    }

    public MutableText insertCommand(String command, int line) {
        if (line < 1 || line - 1 > this.commands.size()) {
            return TextUtils.formattable("Line index " + line + " out of bounds");
        }
        this.commands.add(line - 1, command);
        this.updateArgCount();
        return null;
    }

    public MutableText getSyntax() {
        MutableText out = TextUtils.formattable("/").formatted(Formatting.YELLOW);
        out.append(TextUtils.formattable(this.alias).formatted(Formatting.YELLOW)).append(" ");
        int i = 0;
        for (String arg : this.args) {
            out.append(TextUtils.formattable(arg).formatted(Formatting.YELLOW));
            ++i;
            if (i < this.args.size()) {
                out.append(TextUtils.formattable(separator).formatted(Formatting.YELLOW));
            }
        }
        return out;
    }

    /**
     * Comma separated required arguments.
     * @return MutableText with args (in yellow) separated by commas (in white)
     */
    private MutableText getCommaSeparateArgs() {
        MutableText text = TextUtils.formattable("");
        int i = 0;
        for (String command : this.args) {
            text.append(TextUtils.formattable(command).formatted(Formatting.YELLOW));
            if (++i < this.args.size()) {
                text.append(TextUtils.formattable(", "));
            }
        }
        return text;
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
     * Execute commands with args. TODO: actual arg parsing
     * @param context command context
     * @param args input args
     */
    public void execute(CommandContext<ServerCommandSource> context, String[] args) {
        for (String command : this.commands) {
            int i = 0;
            for (String arg : this.args) {
                command = command.replaceAll("\\{\\$" + arg + "}", args[i]);
                i++;
            }
            ServerCommandSource source = context.getSource();
            CommandDispatcher<ServerCommandSource> dispatcher = source.getServer().getCommandManager().getDispatcher();
            ((CommandSourceModifier) source).setPermissionOverride(true);
            try {
                int result = dispatcher.execute(dispatcher.parse(command, source));
            } catch (CommandSyntaxException e) {
                context.getSource().sendError(TextUtils.formattable(e.getMessage()));
                break;
            }
            ((CommandSourceModifier) source).setPermissionOverride(false);
        }
    }


    /**
     * Executes a command without any args.
     * @param context command context
     */
    public void execute(CommandContext<ServerCommandSource> context) {
        for (String command : this.commands) {
            ServerCommandSource source = context.getSource();
            CommandDispatcher<ServerCommandSource> dispatcher = source.getServer().getCommandManager().getDispatcher();
            ((CommandSourceModifier) source).setPermissionOverride(true);
            try {
                dispatcher.execute(dispatcher.parse(command, source));
            } catch (CommandSyntaxException e) {
                context.getSource().sendError(TextUtils.formattable(e.getMessage()));
                break;
            }
            ((CommandSourceModifier) source).setPermissionOverride(false);
        }
    }

    public boolean writeToFile(Path path) {
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(path)) {
            bufferedWriter.write("Alias: " + this.alias + "\n");
            bufferedWriter.write("Permission level: " + this.permission + "\n");
            if (this.argCount > 0) {
                bufferedWriter.write("Argument separator: \"" + this.separator + "\"\n");
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

    public static boolean readFromFile(MinecraftServer server, Path path) {
        try (BufferedReader bufferedReader = Files.newBufferedReader(path)) {
            boolean readingCommandState = false;
            String line, alias = null, separator = ",";
            int permission = 0;
            List<String> commands = new ArrayList<>();
            while ((line = bufferedReader.readLine()) != null) {
                if (!readingCommandState) {
                    String[] split = line.split(":");
                    if (split.length >= 2) {
                        switch (split[0].toLowerCase()) {
                            case "alias" -> alias = line.replaceFirst("(?i)Alias: *", "");
                            case "argument separator" -> {
                                separator = line.replaceFirst("(?i)Argument separator: *", "");
                                separator = separator.substring(1, separator.length() - 1);
                            }
                            case "permission level" -> {
                                String tmp = line.replaceFirst("(?i)Permission level: *", "");
                                try {
                                    permission = Integer.parseInt(tmp);
                                } catch (NumberFormatException e) {
                                    TechnicalToolbox.log(path + ": Couldn't parse \"" + tmp + "\" as int");
                                    return false;
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
                        commands.add(line.strip());
                    }
                }
            }
            if (alias == null) {
                TechnicalToolbox.log(path + ": Alias not specified in file");
                return false;
            }
            if (commands.size() < 1) {
                TechnicalToolbox.log(path + ": Missing script body");
                return false;
            }
            new AliasedCommand(alias, permission, separator, server.getCommandManager().getDispatcher(), commands);
        }
        catch (IOException e) {
            TechnicalToolbox.warn("Something went wrong reading from file " + path);
        }
        return true;
    }

}
