package com.birblett;

import com.birblett.impl.command.alias.AliasManager;
import com.birblett.impl.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TechnicalToolbox {

    public static final Logger LOGGER = LoggerFactory.getLogger("technical_toolbox");
    public static final ConfigManager CONFIG_MANAGER = new ConfigManager();
    public static final AliasManager ALIAS_MANAGER = new AliasManager();

    public static void log(String str, Object... tok) {
        LOGGER.info("[Technical Toolbox] " + str, tok);
    }

    public static void warn(String str, Object... tok) {
        LOGGER.warn("[Technical Toolbox] " + str, tok);
    }

    public static void error(String str, Object... tok) {
        LOGGER.error("[Technical Toolbox] " + str, tok);
    }

}