package com.smartypantsltd.nylah;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** {@code config/nylah.json}. Every field has a default, so a missing or old file just works. */
public final class NylahConfig {

    public enum NameTag { ALWAYS, LOOKING, NEVER }

    public enum Activity { CALM, NORMAL, PLAYFUL }

    /** Is she in the world at all. */
    public boolean enabled = true;
    /** Sent off for a nap (hidden) from the menu or key; she comes back when called. */
    public boolean napping = false;
    /** Texture density: 2 (default), 4 or 8 texels per model unit. */
    public int textureDetail = 2;
    public NameTag nameTag = NameTag.ALWAYS;
    /** 0..1, multiplies all her sounds. */
    public float volume = 0.8f;
    public Activity activity = Activity.NORMAL;
    /** Her first arrival has happened (she trots in and says hello once, then she is simply there). */
    public boolean firstMeetingDone = false;
    /** How many times she has been stroked. */
    public int strokes = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("nylah.json");
    }

    public static NylahConfig load() {
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                NylahConfig c = GSON.fromJson(r, NylahConfig.class);
                if (c != null) {
                    c.sanitise();
                    return c;
                }
            } catch (Exception e) {
                Nylah.LOG.warn("nylah.json unreadable, using defaults: {}", e.toString());
            }
        }
        NylahConfig c = new NylahConfig();
        c.save();
        return c;
    }

    void sanitise() {
        if (textureDetail != 4 && textureDetail != 8) {
            textureDetail = 2;
        }
        if (nameTag == null) {
            nameTag = NameTag.ALWAYS;
        }
        if (activity == null) {
            activity = Activity.NORMAL;
        }
        volume = Math.max(0f, Math.min(1f, volume));
    }

    public void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (Exception e) {
            Nylah.LOG.warn("Could not save nylah.json: {}", e.toString());
        }
    }
}
