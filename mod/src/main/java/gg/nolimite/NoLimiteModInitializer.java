package gg.nolimite;

import net.fabricmc.api.ClientModInitializer;

/**
 * Einstiegspunkt der Mod. Registriert alles über NoLimiteClient.init().
 */
public class NoLimiteModInitializer implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NoLimiteClient.init();
    }
}