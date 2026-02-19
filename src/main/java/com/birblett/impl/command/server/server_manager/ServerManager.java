package com.birblett.impl.command.server.server_manager;

import com.birblett.TechnicalToolbox;
import com.birblett.util.ConfigUtil;
import com.birblett.util.ServerUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.datafixer.Schemas;
import net.minecraft.world.level.storage.LevelStorage;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

@Environment(EnvType.SERVER)
public class ServerManager {

    public static class ServerManagerConfigOption <T> {

        private final Function<String, T> configReader;
        private final Function<T, String> configWriter;
        private final Supplier<T> defaultValue;

        public T value;

        public ServerManagerConfigOption(Function<String, T> configReader, Function<T, String> configWriter, Supplier<T> defaultValue) {
            this.configReader = configReader;
            this.configWriter = configWriter;
            this.defaultValue = defaultValue;
            this.reset();
        }

        public void reset() {
            this.value = this.defaultValue.get();
        }

        public void setValue(String s) {
             this.value = this.configReader.apply(s);
        }

        public String asString() {
            return this.configWriter.apply(this.value);
        }

        public T get() {
            return this.value;
        }

    }

    public static final LevelStorage SERVER_MANAGER_LEVEL_STORAGE = new LevelStorage(ServerUtil.getBasePath(),
            ServerUtil.getBasePath().resolve("backups"),
            LevelStorage.createSymlinkFinder(Path.of("allowed_symlinks.txt")), Schemas.getFixer());
    public static List<Path> WORLD_PATHS = SERVER_MANAGER_LEVEL_STORAGE.getLevelList().levels().stream().map(LevelStorage.LevelSave::path).toList();
    private static final String SERVER_MANAGER_CONFIG_PATH = "server_manager.conf";
    private static final Map<String, ServerManagerConfigOption<?>> SERVER_MANAGER_CONFIG = new HashMap<>();

    static {
        SERVER_MANAGER_CONFIG.put("locked_worlds",
                new ServerManagerConfigOption<>(
                        s -> new HashSet<>(Arrays.stream(s.split(" +")).filter(str -> !str.isBlank()).toList()),
                        h -> h.stream().reduce("", (s, o) -> s + " " + o),
                        () -> new HashSet<String>()));
        SERVER_MANAGER_CONFIG.put("restart_script", new ServerManagerConfigOption<>(Object::toString, String::strip, () -> "./start.sh"));
    }

    public static ServerManagerConfigOption<?> getOpt(String key) {
        return SERVER_MANAGER_CONFIG.get(key);
    }

    public static <T> T getValue(String key, Class<T> clazz, T defaultValue) {
        try {
            return clazz.cast(SERVER_MANAGER_CONFIG.get(key).get());
        } catch (ClassCastException c) {
            TechnicalToolbox.log("error {}", c);
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean toggleWorldLock(String name) {
        HashSet<String> lockedWorlds = (HashSet<String>) SERVER_MANAGER_CONFIG.get("locked_worlds").get();
        if (!lockedWorlds.remove(name)) {
            lockedWorlds.add(name);
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static boolean isWorldLocked(String name) {
        return ((HashSet<String>) SERVER_MANAGER_CONFIG.get("locked_worlds").get()).contains(name);
    }

    public static void refreshWorlds() {
        ServerManager.WORLD_PATHS = ServerManager.SERVER_MANAGER_LEVEL_STORAGE.getLevelList().levels().stream().map(LevelStorage.LevelSave::path).toList();
    }

    public static void onServerOpen() {
        SERVER_MANAGER_CONFIG.forEach((k, v) -> v.reset());
        ConfigUtil.readConfigs(ServerUtil.getGlobalToolboxPath(), SERVER_MANAGER_CONFIG_PATH,
                (name, value) -> {
                    if (SERVER_MANAGER_CONFIG.containsKey(name)) {
                        SERVER_MANAGER_CONFIG.get(name).setValue(value);
                        return true;
                    }
                    return false;
                },
                (options) -> {
                    TechnicalToolbox.log("Loaded " + options + " valid configuration options from " +
                            "'" + SERVER_MANAGER_CONFIG_PATH + "'");
                    if (SERVER_MANAGER_CONFIG.size() - options > 0) {
                        TechnicalToolbox.log((SERVER_MANAGER_CONFIG.size() - options) + " configuration options were not " +
                                "specified, using defaults");
                    }
                });
    }

    public static void onServerClose() {
        ConfigUtil.writeConfigs(ServerUtil.getGlobalToolboxPath(), SERVER_MANAGER_CONFIG_PATH,
                SERVER_MANAGER_CONFIG.keySet().stream().map(k -> k + " " + SERVER_MANAGER_CONFIG.get(k).asString()));
    }

    public static void restartServer() {

    }

}
