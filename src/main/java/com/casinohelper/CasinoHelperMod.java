package com.casinohelper;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CasinoHelperMod implements ModInitializer {
    public static final String MOD_ID = "casinohelper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Casino Helper Mod v1.0.0");
    }
}

