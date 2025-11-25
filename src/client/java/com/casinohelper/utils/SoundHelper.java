package com.casinohelper.utils;

import com.casinohelper.config.CasinoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;

public class SoundHelper {
    public static final Identifier LOSE_SOUND_ID = Identifier.of("casinohelper", "lose");
    public static final SoundEvent LOSE_SOUND_EVENT = SoundEvent.of(LOSE_SOUND_ID);

    public static final Identifier NEW_BET_SOUND_ID = Identifier.of("casinohelper", "new_bet");
    public static final SoundEvent NEW_BET_SOUND_EVENT = SoundEvent.of(NEW_BET_SOUND_ID);

    public static void register() {
        Registry.register(Registries.SOUND_EVENT, LOSE_SOUND_ID, LOSE_SOUND_EVENT);
        Registry.register(Registries.SOUND_EVENT, NEW_BET_SOUND_ID, NEW_BET_SOUND_EVENT);
    }

    public static void playLoseSound() {
        playSound(LOSE_SOUND_EVENT);
    }

    public static void playNewBetSound() {
        playSound(NEW_BET_SOUND_EVENT);
    }

    private static PositionedSoundInstance currentSound;

    private static void playSound(SoundEvent sound) {
        if (!CasinoConfig.soundEnabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            // Stop previously playing sound if it exists
            if (currentSound != null) {
                client.getSoundManager().stop(currentSound);
            }

            // Create and play new sound
            currentSound = PositionedSoundInstance.master(sound, 1.0f);
            client.getSoundManager().play(currentSound);
        }
    }
}

