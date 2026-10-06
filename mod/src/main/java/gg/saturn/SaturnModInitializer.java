package gg.saturn;

import net.fabricmc.api.ClientModInitializer;

/**
 * Einstiegspunkt der Mod. Registriert alles über SaturnClient.init().
 */
public class SaturnModInitializer implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SaturnClient.init();
    }
}