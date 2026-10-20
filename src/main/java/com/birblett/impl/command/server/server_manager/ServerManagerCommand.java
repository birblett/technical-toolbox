package com.birblett.impl.command.server.server_manager;

import com.birblett.TechnicalToolbox;
import com.birblett.impl.command.ToolboxCommand;
import com.birblett.impl.config.ConfigManager;
import com.birblett.impl.config.ConfigOption;
import com.birblett.util.ServerUtil;
import com.birblett.util.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.CommonColors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

@Environment(EnvType.SERVER)
public class ServerManagerCommand {

    private static final List<String> DEFAULTED_KEYS = List.of("raining", "Time", "DragonFight", "DayTime", "initialized",
            "WanderingTraderSpawnDelay", "CustomBossEvents", "thunderTime", "rainTime", "clearWeatherTime", "LastPlayed", "spawn",
            "ScheduledEvents");
    private static final List<String> COPIED_DIRS = List.of("datapacks", "technical_toolbox", "carpet.conf");
    private static String currentWorld = null;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("server_manager")
                .requires(ServerUtil::hasAdminPerms)
                .then(Commands.literal("list")
                        .executes(ServerManagerCommand::list))
                .then(Commands.literal("refresh")
                        .executes(ServerManagerCommand::refresh))
                .then(Commands.literal("new_world")
                        .then(Commands.argument("world_name", StringArgumentType.string())
                                .then(Commands.argument("world_seed", LongArgumentType.longArg())
                                        .then(Commands.argument("copy_files", BoolArgumentType.bool())
                                                .executes(ServerManagerCommand::createNewWorld)))))
                .then(Commands.literal("download_world")
                        .then(Commands.argument("world_name", StringArgumentType.string())
                                .then(Commands.argument("url", StringArgumentType.greedyString())
                                        .executes(ServerManagerCommand::downloadWorld))))
                .then(Commands.literal("copy_world")
                        .then(Commands.argument("source", StringArgumentType.string())
                                .suggests((c, b) -> listWorlds(c, b, true))
                                .then(Commands.argument("destination", StringArgumentType.string())
                                        .suggests(ServerManagerCommand::listWorldsWriteable)
                                        .executes(ServerManagerCommand::copyWorld))))
                .then(Commands.literal("copy_configs_and_data")
                        .then(Commands.argument("source", StringArgumentType.string())
                                .suggests((c, b) -> listWorlds(c, b, true))
                                .then(Commands.argument("destination", StringArgumentType.string())
                                        .suggests(ServerManagerCommand::listWorldsWriteable)
                                        .executes(ServerManagerCommand::copyConfigsAndData))))
                .then(Commands.literal("delete_world")
                        .then(Commands.argument("world_name", StringArgumentType.string())
                                .suggests(ServerManagerCommand::listWorldsWriteable)
                                        .executes(ServerManagerCommand::deleteWorld)))
                .then(Commands.literal("swap_to")
                        .then(Commands.argument("world_name", StringArgumentType.string())
                                .suggests((c, b) -> listWorlds(c, b, false))
                                .executes(ServerManagerCommand::setCurrentWorld)))
                .then(Commands.literal("toggle_lock")
                        .then(Commands.argument("world_name", StringArgumentType.string())
                                .suggests((c, b) -> listWorlds(c, b, true))
                                .executes(ServerManagerCommand::toggleLock)))
                .then(Commands.literal("restart")
                        .then(Commands.argument("delay", IntegerArgumentType.integer(0))
                                .executes(ServerManagerCommand::restart))
                        .executes(ServerManagerCommand::restart))
                .then(Commands.literal("restart_script")
                        .then(Commands.argument("script", StringArgumentType.greedyString())
                                .executes(ServerManagerCommand::getOrSetRestartScript))
                        .executes(ServerManagerCommand::getOrSetRestartScript))
                .then(Commands.literal("configure_toolbox")
                        .then(Commands.argument("world_name", StringArgumentType.string())
                            .suggests((c, b) -> listWorlds(c, b, false))
                            .then(Commands.argument("config_option", StringArgumentType.string())
                                    .suggests((context, builder) ->
                                            SharedSuggestionProvider.suggest(TechnicalToolbox.CONFIG_MANAGER.getAllConfigOptions(), builder))
                                    .then(Commands.argument("config_value", StringArgumentType.string())
                                            .requires(ServerUtil::hasAdminPerms)
                                            .suggests(ToolboxCommand::configSuggestions)
                                            .executes((ServerManagerCommand::setConfig)))
                                    .executes(ServerManagerCommand::getConfig)))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        ServerUtil.commandFeedback(context, "Worlds:");
        for (Path path : ServerManager.WORLD_PATHS) {
            MutableComponent t = TextUtils.formattable(("  ") + path);
            String s = getCurrentWorld(context);
            String name = path.toString();
            if (ServerManager.isWorldLocked(name)) {
                t = t.withColor(CommonColors.SOFT_RED).append(" \uD83D\uDD12");
            }
            if (name.equals(s)) {
                t.append(TextUtils.formattable(" ★").withColor(CommonColors.GREEN));
            }
            if (!s.equals(ServerManagerCommand.currentWorld) && name.equals(ServerManagerCommand.currentWorld)) {
                t.append(TextUtils.formattable(" ✪").withColor(CommonColors.YELLOW));
            }
            ServerUtil.commandFeedback(context, t);
        }

        return 1;
    }

    private static int refresh(CommandContext<CommandSourceStack> context) {
        ServerManager.refreshWorlds();
        context.getSource().sendSuccess(() -> TextUtils.formattable("Refreshed worlds!").withColor(CommonColors.GREEN), true);
        return list(context);
    }

    private static int createNewWorld(CommandContext<CommandSourceStack> context) {
        String worldName = context.getArgument("world_name", String.class);
        long seed = context.getArgument("world_seed", Long.class);

        if (Files.exists(ServerUtil.getBasePath().resolve(worldName))) {
            context.getSource().sendFailure(Component.nullToEmpty("Directory \"" + worldName + "\" already exists"));
            return 0;
        }

        Path newWorld = null;

        try {
            CompoundTag nbt = NbtIo.readCompressed(ServerUtil.getWorldPath(context.getSource().getServer()).resolve("level.dat"), NbtAccounter.unlimitedHeap());

            if (nbt == null) {
                throw new RuntimeException();
            }

            newWorld = Files.createTempDirectory(ServerUtil.getBasePath(), "temp_" + worldName);

            CompoundTag newProperties = nbt.copy();
            CompoundTag data = nbt.read("Data", CompoundTag.CODEC).orElse(new CompoundTag());
            CompoundTag worldGen = data.read("WorldGenSettings", CompoundTag.CODEC).orElse(new CompoundTag());
            worldGen.putLong("seed", seed);
            data.put("WorldGenSettings", worldGen);
            data.putString("LevelName", worldName);
            for (String key : DEFAULTED_KEYS) {
                data.remove(key);
            }
            newProperties.put("Data", data);

            NbtIo.writeCompressed(newProperties, newWorld.resolve("level.dat"));

            if (context.getArgument("copy_files", Boolean.class)) {
                Path source = ServerUtil.getWorldPath(context.getSource().getServer());
                copyDirs(context, newWorld, source);
            }

            FileUtils.copyDirectory(new File(newWorld.toUri()), new File(ServerUtil.getBasePath().resolve(worldName).toUri()));
            FileUtils.deleteDirectory(new File(newWorld.toUri()));
            ServerManager.refreshWorlds();
        } catch (Exception e) {
            if (newWorld != null) {
                try {
                    FileUtils.deleteDirectory(new File(newWorld.toUri()));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
            context.getSource().sendFailure(Component.nullToEmpty("Something went wrong creating the world: " + e));
            return 0;
        }

        return ServerManagerCommand.list(context);
    }

    private static int downloadWorld(CommandContext<CommandSourceStack> context) {
        String worldName = context.getArgument("world_name", String.class);
        String urlString = context.getArgument("url", String.class);
        Runnable download = () -> {
            ServerUtil.commandFeedback(context, "Attempting world download...");
            Path newWorld = null;
            try {
                newWorld = Files.createTempDirectory(ServerUtil.getBasePath(), "temp_" + worldName);
                Path zip = newWorld.resolve("tmp.zip");
                URL url = URI.create(urlString).toURL();
                if (isValidZip(url)) {
                    try (InputStream in = url.openStream()) {
                        Files.copy(in, zip, StandardCopyOption.REPLACE_EXISTING);
                    }
                    ServerUtil.commandFeedback(context, "Finished download, extracting...");

                    try (ZipFile zipFile = new ZipFile(zip.toFile())) {
                        Enumeration<? extends ZipEntry> entries = zipFile.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry entry = entries.nextElement();
                            File dest = new File(newWorld.toFile(), entry.getName());
                            if (entry.isDirectory()) {
                                dest.mkdirs();
                            } else {
                                dest.getParentFile().mkdirs();
                                try (InputStream in = zipFile.getInputStream(entry);
                                     OutputStream out = new FileOutputStream(dest)) {
                                    IOUtils.copy(in, out);
                                }
                            }
                        }
                    }
                    FileUtils.delete(zip.toFile());
                    ServerUtil.commandFeedback(context, "Successfully extracted archive, validating...");

                    if (!Files.exists(newWorld.resolve("level.dat"))) {
                        try (Stream<Path> stream = Files.list(newWorld)) {
                            Optional<Path> path = stream.filter(p ->
                                    Files.isDirectory(p) && Files.exists(p.resolve("level.dat"))).findFirst();
                            if (path.isPresent()) {
                                ServerUtil.commandFeedback(context, "Found nested directories, restructuring...");
                                Path p = path.get();
                                File[] list = p.toFile().listFiles();
                                if (list == null) {
                                    throw new Exception("couldn't parse directory " + p);
                                }
                                for (File file : list) {
                                    Files.move(file.toPath(), newWorld.resolve(file.getName()));
                                }
                            } else {
                                throw new Exception("no level.dat found");
                            }
                        }
                    }
                    ServerUtil.commandFeedback(context, "Successfully downloaded world \"" + worldName + "\", cleaning up...");

                    FileUtils.copyDirectory(new File(newWorld.toUri()), new File(ServerUtil.getBasePath().resolve(worldName).toUri()));
                    FileUtils.deleteDirectory(new File(newWorld.toUri()));
                    ServerManager.refreshWorlds();
                }
            } catch (Exception e) {
                if (newWorld != null) {
                    try {
                        FileUtils.deleteDirectory(new File(newWorld.toUri()));
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                context.getSource().sendFailure(Component.nullToEmpty("Something went wrong downloading the world: " + e));
            }
        };
        (new Thread(download)).start();
        return 0;
    }

    private static CompletableFuture<Suggestions> listWorlds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder, boolean includeCurrent) {
        String current = context.getSource().getServer().getWorldData().getLevelName();
        return SharedSuggestionProvider.suggest(ServerManager.WORLD_PATHS.stream().map(Path::toString).filter(s -> includeCurrent || !current.equals(s)), builder);
    }

    private static CompletableFuture<Suggestions> listWorldsWriteable(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String current = context.getSource().getServer().getWorldData().getLevelName();
        String source;
        try {
            source = context.getArgument("source", String.class);
        } catch(Exception e) {
            source = "";
        }
        String checkedSource = source;
        return SharedSuggestionProvider.suggest(ServerManager.WORLD_PATHS.stream().map(Path::toString)
                .filter(s -> !current.equals(s) && !checkedSource.equals(s) && !ServerManager.isWorldLocked(s)) , builder);
    }

    private static CompletableFuture<Suggestions> listWorldsNoSource(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String dest = context.getArgument("source", String.class);
        return SharedSuggestionProvider.suggest(ServerManager.WORLD_PATHS.stream().map(Path::toString).filter(s -> !dest.equals(s)), builder);
    }

    private static int copyWorld(CommandContext<CommandSourceStack> context) {
        Path source = ServerUtil.getBasePath().resolve(context.getArgument("source", String.class));
        Path dest = ServerUtil.getBasePath().resolve(context.getArgument("destination", String.class));
        if (!Files.exists(source)) {
            context.getSource().sendFailure(Component.nullToEmpty("World \"" + source + "\" not found"));
            return 0;
        }
        if (Files.exists(dest)) {
            context.getSource().sendFailure(Component.nullToEmpty("Directory \"" + dest + "\" already exists"));
            return 0;
        }
        try {
            FileUtils.copyDirectory(new File(source.toUri()), new File(dest.toUri()));
        } catch (IOException e) {
            context.getSource().sendFailure(Component.nullToEmpty("Failed to create copy of \"" + source + "\": " + e));
        }
        ServerManager.refreshWorlds();
        return list(context);
    }

    private static int deleteWorld(CommandContext<CommandSourceStack> context) {
        String name = context.getArgument("world_name", String.class);
        Path world = ServerUtil.getBasePath().resolve(name);
        if (!Files.exists(world)) {
            context.getSource().sendFailure(Component.nullToEmpty("World \"" + name + "\" not found"));
            return 0;
        }
        if (context.getSource().getServer().getWorldData().getLevelName().equals(name)) {
            context.getSource().sendFailure(Component.nullToEmpty("Can't delete the active world"));
            return 0;
        }
        if (ServerManager.isWorldLocked(name)) {
            context.getSource().sendFailure(Component.nullToEmpty("World \"" + name + "\" is locked and can't be modified"));
            return 0;
        }
        try {
            FileUtils.deleteDirectory(new File(world.toUri()));
            ServerUtil.commandFeedback(context, "Deleted world \"" + name + "\"");
        } catch (IOException e) {
            context.getSource().sendFailure(Component.nullToEmpty("Failed to delete world \"" + world + "\": " + e));
            return 0;
        }
        ServerManager.refreshWorlds();
        return list(context);
    }

    private static int copyConfigsAndData(CommandContext<CommandSourceStack> context) {
        Path dest = ServerUtil.getBasePath().resolve(context.getArgument("destination", String.class));
        Path source = ServerUtil.getBasePath().resolve(context.getArgument("source", String.class));
        Path t;
        if (!Files.exists(t = dest) || !Files.exists(t = source)) {
            context.getSource().sendFailure(Component.nullToEmpty("World \"" + t + "\" not found"));
            return 0;
        }
        if (ServerManager.isWorldLocked(dest.toString())) {
            context.getSource().sendFailure(Component.nullToEmpty("World \"" + t + "\" is locked and can't be modified"));
            return 0;
        }
        copyDirs(context, dest, source);
        return 1;
    }

    private static int setCurrentWorld(CommandContext<CommandSourceStack> context) {
        String name = getWorld(context);
        if (name == null) {
            return 0;
        }
        try {
            Path properties = ServerUtil.getBasePath().resolve("server.properties");
            List<String> lines = Files.readAllLines(properties);
            Files.write(properties, lines.stream().map(s -> s.replaceFirst("level-name=.*", "level-name=" + name)).toList());
            ServerUtil.commandFeedback(context, "Server world set to \"" + name + "\", will be applied on restart");
            ServerManagerCommand.currentWorld = name;
        } catch (IOException e) {
            context.getSource().sendFailure(Component.nullToEmpty("Failed to change default world: " + e));
            return 0;
        }
        return list(context);
    }

    private static int toggleLock(CommandContext<CommandSourceStack> context) {
        String name = getWorld(context);
        if (name == null) {
            return 0;
        }
        ServerUtil.commandFeedback(context, (ServerManager.toggleWorldLock(name) ? "Locked " : "Unlocked ") + "world \"" + name + "\"");
        return list(context);
    }

    private static int restart(CommandContext<CommandSourceStack> context) {
        String script = ServerManager.getValue("restart_script", String.class, "./start.sh");
        try {
            int delay = context.getArgument("delay", Integer.class);
        } catch (Exception e) {
            try {
                Runtime.getRuntime().exec(new String[]{ script }, null);
                TechnicalToolbox.log("{} restarted the server via server_manager", context.getSource().getTextName());
                context.getSource().getServer().halt(false);
            } catch (IOException ex) {
                context.getSource().sendFailure(Component.nullToEmpty("Encountered an error restarting the server: " + ex));
            }
        }
        return 0;
    }

    private static int getOrSetRestartScript(CommandContext<CommandSourceStack> context) {
        try {
            String script = context.getArgument("path", String.class);
            ServerManager.getOpt("restart_script").setValue(script);
            ServerUtil.commandFeedback(context, "Startup script set to \"" + script + "\"");
        } catch (Exception e) {
            ServerUtil.commandFeedback(context, "Startup script is set to \"" +
                    ServerManager.getValue("restart_script", String.class, "start.sh") + "\"");
        }
        return 0;
    }

    private static void copyDirs(CommandContext<CommandSourceStack> context, Path newWorld, Path source) {
        for (String dir : COPIED_DIRS) {
            try {
                File p = new File(source.resolve(dir).toUri());
                if (FileUtils.isDirectory(p)) {
                    FileUtils.copyDirectory(p, new File(newWorld.resolve(dir).toUri()));
                } else {
                    FileUtils.copyFile(p, new File(newWorld.resolve(dir).toUri()));
                }
            } catch (IOException e) {
                ServerUtil.commandFeedback(context, dir + " not found, skipping");
            }
        }
    }

    private static int setConfig(CommandContext<CommandSourceStack> context) {
        String name = getWorld(context);
        if (name == null) {
            return 1;
        }
        if (name.equals(getCurrentWorld(context))) {
            context.getSource().sendFailure(Component.nullToEmpty("Please use /toolbox config to configure the current world"));
            return 1;
        }
        String option = context.getArgument("config_option", String.class);
        String value = context.getArgument("config_value", String.class);
        ConfigManager m = getTemporaryConfig(ServerUtil.getBasePath().resolve(name));
        if (m.getAllConfigOptions().contains(option)) {
            ConfigOption<?> c = m.get(option);
            Component out = c.setFromString(value, null);
            if (out != null) {
                context.getSource().sendFailure(out);
                return 1;
            } else {
                context.getSource().sendSuccess(() -> TextUtils.formattable("Successfully set value ").append(
                        TextUtils.formattable(value).setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN))).append(
                        TextUtils.formattable(" for option " + option + " on world \"" + name + "\"")), true);
                m.writeConfigs(ServerUtil.getBasePath().resolve(name + "/technical_toolbox"));
                return 1;
            }
        } else {
            context.getSource().sendFailure(TextUtils.formattable("No config option with name \"" + option + "\""));
            return 0;
        }
    }

    private static int getConfig(CommandContext<CommandSourceStack> context) {
        String name = getWorld(context);
        if (name == null) {
            return 1;
        }
        String option = context.getArgument("config_option", String.class);
        ConfigManager m = getTemporaryConfig(ServerUtil.getBasePath().resolve(name));
        if (m.getAllConfigOptions().contains(option)) {
            ConfigOption<?> c = m.get(option);
            context.getSource().sendSuccess(c::getText, true);
        } else {
            context.getSource().sendFailure(TextUtils.formattable("No config option with name \"" + option + "\""));
        }
        return 0;
    }

    private static ConfigManager getTemporaryConfig(Path basePath) {
        ConfigManager m = new ConfigManager();
        if (Files.exists(basePath.resolve("technical_toolbox/" + ConfigManager.CONFIG_PATH))) {
            m.readConfigs(basePath.resolve("technical_toolbox/"), null);
        }
        return m;
    }

    private static String getWorld(CommandContext<CommandSourceStack> context) {
        String name = context.getArgument("world_name", String.class);
        Path path = ServerUtil.getBasePath().resolve(name);
        if (!Files.exists(path)) {
            context.getSource().sendFailure(Component.nullToEmpty("World directory \"" + path + "\" not found"));
            return null;
        }
        return name;
    }

    private static String getCurrentWorld(CommandContext<CommandSourceStack> context) {
        String s = context.getSource().getServer().getWorldData().getLevelName();
        if (ServerManagerCommand.currentWorld == null) {
            ServerManagerCommand.currentWorld = s;
        }
        return s;
    }

    private static boolean isValidZip(URL url) {
        try {
            URLConnection connection = url.openConnection();
            try (ZipInputStream stream = new ZipInputStream(new BufferedInputStream(connection.getInputStream()))) {
                return stream.getNextEntry() != null;
            }
        } catch (IOException e) {
            return false;
        }
    }

}
