package com.smartypantsltd.nylah;

import com.smartypantsltd.nylah.entity.NylahCat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.feline.CatSoundVariant;
import net.minecraft.world.entity.animal.feline.CatSoundVariants;

/**
 * Her voice: real cat sounds, played locally only ({@code playLocalSound}), so
 * nobody else on the server hears anything. Volume follows her setting.
 */
final class Voice {

    private int purrCooldown;

    void tick() {
        if (purrCooldown > 0) {
            purrCooldown--;
        }
    }

    /** Play a clip event ("purr", "meow", "trill", "chirp"...). Unknown events are silent. */
    void play(NylahCat cat, String event, float volume) {
        if (volume <= 0f || cat == null || !(cat.level() instanceof ClientLevel level)) {
            return;
        }
        CatSoundVariant.CatSoundSet s = sounds();
        if (s == null) {
            return;
        }
        switch (event) {
            case "purr" -> {
                if (purrCooldown == 0) {
                    emit(level, cat, s.purrSound().value(), 0.55f * volume, 1.0f);
                    purrCooldown = 36;
                }
            }
            case "meow" -> emit(level, cat, s.ambientSound().value(), 0.6f * volume, 1.08f);
            case "trill" -> emit(level, cat, s.purreowSound().value(), 0.55f * volume, 1.3f);
            case "chirp" -> emit(level, cat, s.purreowSound().value(), 0.4f * volume, 1.55f);
            case "yawn" -> emit(level, cat, s.purreowSound().value(), 0.25f * volume, 0.75f);
            case "beg" -> emit(level, cat, s.begForFoodSound().value(), 0.5f * volume, 1.1f);
            default -> {
                // lick, stretch, sleep, pounce, sneeze: quiet things
            }
        }
    }

    private static void emit(ClientLevel level, NylahCat cat, SoundEvent sound, float vol, float pitch) {
        float jitter = 0.94f + (float) Math.random() * 0.12f;
        level.playLocalSound(cat.getX(), cat.getY() + 0.3, cat.getZ(), sound, SoundSource.NEUTRAL, vol, pitch * jitter, false);
    }

    private static CatSoundVariant.CatSoundSet sounds() {
        CatSoundVariant v = SoundEvents.CAT_SOUNDS.get(CatSoundVariants.SoundSet.CLASSIC);
        return v == null ? null : v.adultSounds();
    }
}
