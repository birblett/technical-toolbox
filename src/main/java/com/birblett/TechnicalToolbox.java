package com.birblett;

import com.birblett.util.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TechnicalToolbox {

    public static final Logger LOGGER = LoggerFactory.getLogger("technical-toolbox");
    public static final ConfigManager CONFIG_MANAGER = new ConfigManager();

    public static void log(String str) {
        LOGGER.info("[Technical Toolbox] {}", str);
    }

    public static void warn(String str) {
        LOGGER.warn("[Technical Toolbox] {}", str);
    }

    public static void error(String str) {
        LOGGER.error("[Technical Toolbox] {}", str);
    }

}