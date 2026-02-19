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
import net.minecraft.command.CommandSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
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

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("server_manager")
                .requires(ServerUtil::hasAdminPerms)
                .then(CommandManager.literal("list")
                        .executes(ServerManagerCommand::list))
                .then(CommandManager.literal("refresh")
                        .executes(ServerManagerCommand::refresh))
                .then(CommandManager.literal("new_world")
                        .then(CommandManager.argument("world_name", StringArgumentType.string())
                                .then(CommandManager.argument("world_seed", LongArgumentType.longArg())
                                        .then(CommandManager.argument("copy_files", BoolArgumentType.bool())
                                                .executes(ServerManagerCommand::createNewWorld)))))
                .then(CommandManager.literal("download_world")
                        .then(CommandManager.argument("world_name", StringArgumentType.string())
                                .then(CommandManager.argument("url", StringArgumentType.greedyString())
                                        .executes(ServerManagerCommand::downloadWorld))))
                .then(CommandManager.literal("copy_world")
                        .then(CommandManager.argument("source", StringArgumentType.string())
                                .suggests((c, b) -> listWorlds(c, b, true))
                                .then(CommandManager.argument("destination", StringArgumentType.string())
                                        .suggests(ServerManagerCommand::listWorldsWriteable)
                                        .executes(ServerManagerCommand::copyWorld))))
                .then(CommandManager.literal("copy_configs_and_data")
                        .then(CommandManager.argument("source", StringArgumentType.string())
                                .suggests((c, b) -> listWorlds(c, b, true))
                                .then(CommandManager.argument("destination", StringArgumentType.string())
                                        .suggests(ServerManagerCommand::listWorldsWriteable)
                                        .executes(ServerManagerCommand::copyConfigsAndData))))
                .then(CommandManager.literal("delete_world")
                        .then(CommandManager.argument("world_name", StringArgumentType.string())
                                .suggests(ServerManagerCommand::listWorldsWriteable)
                                        .executes(ServerManagerCommand::deleteWorld)))
                .then(CommandManager.literal("swap_to")
                        .then(CommandManager.argument("world_name", StringArgumentType.string())
                                .suggests((c, b) -> listWorlds(c, b, false))
                                .executes(ServerManagerCommand::setCurrentWorld)))
                .then(CommandManager.literal("toggle_lock")
                        .then(CommandManager.argument("world_name", StringArgumentType.string())
                                .suggests((c, b) -> listWorlds(c, b, true))
                                .executes(ServerManagerCommand::toggleLock)))
                .then(CommandManager.literal("restart")
                        .then(CommandManager.argument("delay", IntegerArgumentType.integer(0))
                                .executes(ServerManagerCommand::restart))
                        .executes(ServerManagerCommand::restart))
                .then(CommandManager.literal("restart_script")
                        .then(CommandManager.argument("script", StringArgumentType.greedyString())
                                .executes(ServerManagerCommand::getOrSetRestartScript))
                        .executes(ServerManagerCommand::getOrSetRestartScript))
                .then(CommandManager.literal("configure_toolbox")
                        .then(CommandManager.argument("world_name", StringArgumentType.string())
                            .suggests((c, b) -> listWorlds(c, b, false))
                            .then(CommandManager.argument("config_option", StringArgumentType.string())
                                    .suggests((context, builder) ->
                                            CommandSource.suggestMatching(TechnicalToolbox.CONFIG_MANAGER.getAllConfigOptions(), builder))
                                    .then(CommandManager.argument("config_value", StringArgumentType.string())
                                            .requires(ServerUtil::hasAdminPerms)
                                            .suggests(ToolboxCommand::configSuggestions)
                                            .executes((ServerManagerCommand::setConfig)))
                                    .executes(ServerManagerCommand::getConfig)))));
    }

    private static int list(CommandContext<ServerCommandSource> context) {
        context.getSource().sendMessage(TextUtils.formattable("Worlds:"));
        for (Path path : ServerManager.WORLD_PATHS) {
            MutableText t = TextUtils.formattable(("  ") + path);
            String s = getCurrentWorld(context);
            String name = path.toString();
            if (ServerManager.isWorldLocked(name)) {
                t = t.withColor(Colors.LIGHT_RED).append(" \uD83D\uDD12");
            }
            if (name.equals(s)) {
                t.append(TextUtils.formattable(" ★").withColor(Colors.GREEN));
            }
            if (!s.equals(ServerManagerCommand.currentWorld) && name.equals(ServerManagerCommand.currentWorld)) {
                t.append(TextUtils.formattable(" ✪").withColor(Colors.YELLOW));
            }
            context.getSource().sendMessage(t);
        }

        return 1;
    }

    private static int refresh(CommandContext<ServerCommandSource> context) {
        ServerManager.refreshWorlds();
        context.getSource().sendFeedback(() -> TextUtils.formattable("Refreshed worlds!").withColor(Colors.GREEN), true);
        return list(context);
    }

    private static int createNewWorld(CommandContext<ServerCommandSource> context) {
        String worldName = context.getArgument("world_name", String.class);
        long seed = context.getArgument("world_seed", Long.class);

        if (Files.exists(ServerUtil.getBasePath().resolve(worldName))) {
            context.getSource().sendError(Text.of("Directory \"" + worldName + "\" already exists"));
            return 0;
        }

        Path newWorld = null;

        try {
            NbtCompound nbt = NbtIo.readCompressed(ServerUtil.getWorldPath(context.getSource().getServer()).resolve("level.dat"), NbtSizeTracker.ofUnlimitedBytes());

            if (nbt == null) {
                throw new RuntimeException();
            }

            newWorld = Files.createTempDirectory(ServerUtil.getBasePath(), "temp_" + worldName);

            NbtCompound newProperties = nbt.copy();
            NbtCompound data = nbt.get("Data", NbtCompound.CODEC).orElse(new NbtCompound());
            NbtCompound worldGen = data.get("WorldGenSettings", NbtCompound.CODEC).orElse(new NbtCompound());
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
            context.getSource().sendError(Text.of("Something went wrong creating the world: " + e));
            return 0;
        }

        return ServerManagerCommand.list(context);
    }

    private static int downloadWorld(CommandContext<ServerCommandSource> context) {
        String worldName = context.getArgument("world_name", String.class);
        String urlString = context.getArgument("url", String.class);
        Runnable download = () -> {
            context.getSource().sendMessage(Text.of("Attempting world download..."));
            Path newWorld = null;
            try {
                newWorld = Files.createTempDirectory(ServerUtil.getBasePath(), "temp_" + worldName);
                Path zip = newWorld.resolve("tmp.zip");
                URL url = URI.create(urlString).toURL();
                if (isValidZip(url)) {
                    try (InputStream in = url.openStream()) {
                        Files.copy(in, zip, StandardCopyOption.REPLACE_EXISTING);
                    }
                    context.getSource().sendMessage(Text.of("Finished download, extracting..."));

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
                    context.getSource().sendMessage(Text.of("Successfully extracted archive, validating..."));

                    if (!Files.exists(newWorld.resolve("level.dat"))) {
                        try (Stream<Path> stream = Files.list(newWorld)) {
                            Optional<Path> path = stream.filter(p ->
                                    Files.isDirectory(p) && Files.exists(p.resolve("level.dat"))).findFirst();
                            if (path.isPresent()) {
                                context.getSource().sendMessage(Text.of("Found nested directories, restructuring..."));
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

                    context.getSource().sendMessage(Text.of("Successfully downloaded world \"" + worldName + "\", cleaning up..."));
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
                context.getSource().sendError(Text.of("Something went wrong downloading the world: " + e));
            }
        };
        (new Thread(download)).start();
        return 0;
    }

    private static CompletableFuture<Suggestions> listWorlds(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder, boolean includeCurrent) {
        String current = context.getSource().getServer().getSaveProperties().getLevelName();
        return CommandSource.suggestMatching(ServerManager.WORLD_PATHS.stream().map(Path::toString).filter(s -> includeCurrent || !current.equals(s)), builder);
    }

    private static CompletableFuture<Suggestions> listWorldsWriteable(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        String current = context.getSource().getServer().getSaveProperties().getLevelName();
        String source;
        try {
            source = context.getArgument("source", String.class);
        } catch(Exception e) {
            source = "";
        }
        String checkedSource = source;
        return CommandSource.suggestMatching(ServerManager.WORLD_PATHS.stream().map(Path::toString)
                .filter(s -> !current.equals(s) && !checkedSource.equals(s) && !ServerManager.isWorldLocked(s)) , builder);
    }

    private static CompletableFuture<Suggestions> listWorldsNoSource(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        String dest = context.getArgument("source", String.class);
        return CommandSource.suggestMatching(ServerManager.WORLD_PATHS.stream().map(Path::toString).filter(s -> !dest.equals(s)), builder);
    }

    private static int copyWorld(CommandContext<ServerCommandSource> context) {
        Path source = ServerUtil.getBasePath().resolve(context.getArgument("source", String.class));
        Path dest = ServerUtil.getBasePath().resolve(context.getArgument("destination", String.class));
        if (!Files.exists(source)) {
            context.getSource().sendError(Text.of("World \"" + source + "\" not found"));
            return 0;
        }
        if (Files.exists(dest)) {
            context.getSource().sendError(Text.of("Directory \"" + dest + "\" already exists"));
            return 0;
        }
        try {
            FileUtils.copyDirectory(new File(source.toUri()), new File(dest.toUri()));
        } catch (IOException e) {
            context.getSource().sendError(Text.of("Failed to create copy of \"" + source + "\": " + e));
        }
        ServerManager.refreshWorlds();
        return list(context);
    }

    private static int deleteWorld(CommandContext<ServerCommandSource> context) {
        String name = context.getArgument("world_name", String.class);
        Path world = ServerUtil.getBasePath().resolve(name);
        if (!Files.exists(world)) {
            context.getSource().sendError(Text.of("World \"" + name + "\" not found"));
            return 0;
        }
        if (context.getSource().getServer().getSaveProperties().getLevelName().equals(name)) {
            context.getSource().sendError(Text.of("Can't delete the active world"));
            return 0;
        }
        if (ServerManager.isWorldLocked(name)) {
            context.getSource().sendError(Text.of("World \"" + name + "\" is locked and can't be modified"));
            return 0;
        }
        try {
            FileUtils.deleteDirectory(new File(world.toUri()));
            context.getSource().sendMessage(Text.of("Deleted world \"" + name + "\""));
        } catch (IOException e) {
            context.getSource().sendError(Text.of("Failed to delete world \"" + world + "\": " + e));
            return 0;
        }
        ServerManager.refreshWorlds();
        return list(context);
    }

    private static int copyConfigsAndData(CommandContext<ServerCommandSource> context) {
        Path dest = ServerUtil.getBasePath().resolve(context.getArgument("destination", String.class));
        Path source = ServerUtil.getBasePath().resolve(context.getArgument("source", String.class));
        Path t;
        if (!Files.exists(t = dest) || !Files.exists(t = source)) {
            context.getSource().sendError(Text.of("World \"" + t + "\" not found"));
            return 0;
        }
        if (ServerManager.isWorldLocked(dest.toString())) {
            context.getSource().sendError(Text.of("World \"" + t + "\" is locked and can't be modified"));
            return 0;
        }
        copyDirs(context, dest, source);
        return 1;
    }

    private static int setCurrentWorld(CommandContext<ServerCommandSource> context) {
        String name = getWorld(context);
        if (name == null) {
            return 0;
        }
        try {
            Path properties = ServerUtil.getBasePath().resolve("server.properties");
            List<String> lines = Files.readAllLines(properties);
            Files.write(properties, lines.stream().map(s -> s.replaceFirst("level-name=.*", "level-name=" + name)).toList());
            context.getSource().sendMessage(Text.of("Server world set to \"" + name + "\", will be applied on restart"));
            ServerManagerCommand.currentWorld = name;
        } catch (IOException e) {
            context.getSource().sendError(Text.of("Failed to change default world: " + e));
            return 0;
        }
        return list(context);
    }

    private static int toggleLock(CommandContext<ServerCommandSource> context) {
        String name = getWorld(context);
        if (name == null) {
            return 0;
        }
        context.getSource().sendMessage(Text.of((ServerManager.toggleWorldLock(name) ? "Locked " : "Unlocked ") + "world \"" + name + "\""));
        return list(context);
    }

    private static int restart(CommandContext<ServerCommandSource> context) {
        String script = ServerManager.getValue("restart_script", String.class, "./start.sh");
        try {
            int delay = context.getArgument("delay", Integer.class);
        } catch (Exception e) {
            try {
                Runtime.getRuntime().exec(new String[]{ script }, null);
                TechnicalToolbox.log("{} restarted the server via server_manager", context.getSource().getName());
                context.getSource().getServer().stop(false);
            } catch (IOException ex) {
                context.getSource().sendError(Text.of("Encountered an error restarting the server: " + ex));
            }
        }
        return 0;
    }

    private static int getOrSetRestartScript(CommandContext<ServerCommandSource> context) {
        try {
            String script = context.getArgument("path", String.class);
            ServerManager.getOpt("restart_script").setValue(script);
            context.getSource().sendMessage(Text.of("Startup script set to \"" + script + "\""));
        } catch (Exception e) {
            context.getSource().sendMessage(Text.of("Startup script is set to \"" +
                    ServerManager.getValue("restart_script", String.class, "start.sh") + "\""));
        }
        return 0;
    }

    private static void copyDirs(CommandContext<ServerCommandSource> context, Path newWorld, Path source) {
        for (String dir : COPIED_DIRS) {
            try {
                File p = new File(source.resolve(dir).toUri());
                if (FileUtils.isDirectory(p)) {
                    FileUtils.copyDirectory(p, new File(newWorld.resolve(dir).toUri()));
                } else {
                    FileUtils.copyFile(p, new File(newWorld.resolve(dir).toUri()));
                }
            } catch (IOException e) {
                context.getSource().sendMessage(Text.of(dir + " not found, skipping"));
            }
        }
    }

    private static int setConfig(CommandContext<ServerCommandSource> context) {
        String name = getWorld(context);
        if (name == null) {
            return 1;
        }
        if (name.equals(getCurrentWorld(context))) {
            context.getSource().sendError(Text.of("Please use /toolbox config to configure the current world"));
            return 1;
        }
        String option = context.getArgument("config_option", String.class);
        String value = context.getArgument("config_value", String.class);
        ConfigManager m = getTemporaryConfig(ServerUtil.getBasePath().resolve(name));
        if (m.getAllConfigOptions().contains(option)) {
            ConfigOption<?> c = m.get(option);
            Text out = c.setFromString(value, null);
            if (out != null) {
                context.getSource().sendError(out);
                return 1;
            } else {
                context.getSource().sendFeedback(() -> TextUtils.formattable("Successfully set value ").append(
                        TextUtils.formattable(value).setStyle(Style.EMPTY.withColor(Formatting.GREEN))).append(
                        TextUtils.formattable(" for option " + option + " on world \"" + name + "\"")), true);
                m.writeConfigs(ServerUtil.getBasePath().resolve(name + "/technical_toolbox"));
                return 1;
            }
        } else {
            context.getSource().sendError(TextUtils.formattable("No config option with name \"" + option + "\""));
            return 0;
        }
    }

    private static int getConfig(CommandContext<ServerCommandSource> context) {
        String name = getWorld(context);
        if (name == null) {
            return 1;
        }
        String option = context.getArgument("config_option", String.class);
        ConfigManager m = getTemporaryConfig(ServerUtil.getBasePath().resolve(name));
        if (m.getAllConfigOptions().contains(option)) {
            ConfigOption<?> c = m.get(option);
            context.getSource().sendFeedback(c::getText, true);
        } else {
            context.getSource().sendError(TextUtils.formattable("No config option with name \"" + option + "\""));
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

    private static String getWorld(CommandContext<ServerCommandSource> context) {
        String name = context.getArgument("world_name", String.class);
        Path path = ServerUtil.getBasePath().resolve(name);
        if (!Files.exists(path)) {
            context.getSource().sendError(Text.of("World directory \"" + path + "\" not found"));
            return null;
        }
        return name;
    }

    private static String getCurrentWorld(CommandContext<ServerCommandSource> context) {
        String s = context.getSource().getServer().getSaveProperties().getLevelName();
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
