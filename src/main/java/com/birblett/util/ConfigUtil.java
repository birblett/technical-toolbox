package com.birblett.util;

import com.birblett.TechnicalToolbox;
import org.apache.commons.lang3.mutable.MutableInt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ConfigUtil {

    /**
     * Generic .conf file reader
     */
    public static void readConfigs(Path basePath, String configName, BiFunction<String, String, Boolean> optionValueReader, Consumer<Integer> onCompletion) {
        try (BufferedReader bufferedReader = Files.newBufferedReader(basePath.resolve(configName))) {
            String line;
            int lineCount = 0;
            int options = 0;
            while ((line = bufferedReader.readLine()) != null) {
                lineCount++;
                String[] split = line.split("(: *| +)", 2);
                if (split.length == 0 || (split[0].isBlank())) {
                } else if (split.length != 2) {
                    TechnicalToolbox.error("Improperly separated config option on line " +
                            lineCount + " ('" + line + "')");
                } else {
                    String name = split[0].strip();
                    String value = split[1].strip();
                    if (optionValueReader.apply(name, value)) {
                        options++;
                    }
                }
            }
            onCompletion.accept(options);
        } catch (Exception e) {
            TechnicalToolbox.warn("Configuration file '{}' was not found, generating empty config", configName);
            if (ServerUtil.createDirectoryIfNotPresent(basePath.toFile())) {
                try (BufferedWriter bufferedWriter = Files.newBufferedWriter(basePath.resolve(configName))) {
                    bufferedWriter.write("");
                } catch (IOException ex) {
                    TechnicalToolbox.error("Failed to generate configuration file '{}'", configName);
                }
            }
        }
    }

    /**
     * Generic .conf file writer
     */
    public static void writeConfigs(Path basePath, String configName, Stream<String> writeableConfigs) {
        if (ServerUtil.createDirectoryIfNotPresent(basePath.toFile())) {
            try (BufferedWriter bufferedWriter = Files.newBufferedWriter(basePath.resolve(configName))) {
                MutableInt options = new MutableInt(0);

                writeableConfigs.forEach(s -> {
                    if (s == null) {
                        return;
                    }
                    try {
                        bufferedWriter.write(s);
                        bufferedWriter.write("\n");
                        options.add(1);
                    } catch (IOException e) {
                        TechnicalToolbox.error("Failed to write to file '{}', configurations will not be saved: {}", configName, e);
                    }
                });
                TechnicalToolbox.log("Wrote " + options.get() + " configuration options to '{}'", configName);
            } catch (IOException e) {
                TechnicalToolbox.error("Failed to write to file '{}', configurations will not be saved: {}", configName, e);
            }
        }
    }

}
