package com.smartypantsltd.nylah.ui;

import com.smartypantsltd.nylah.Nylah;
import com.smartypantsltd.nylah.NylahConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Her menu (N): ask her to do any of her things, and her settings.
 * A purely local screen: it opens no container and sends nothing.
 */
public final class NylahScreen extends Screen {

    /** Clip name to a friendly button label, in the order they are shown. */
    private static final Map<String, String> ACTIONS = new LinkedHashMap<>();

    static {
        ACTIONS.put("look_up", "Look up at me");
        ACTIONS.put("slow_blink", "Slow blink");
        ACTIONS.put("blep", "Blep");
        ACTIONS.put("lick_lips", "Lick lips");
        ACTIONS.put("head_bonk", "Head bonk");
        ACTIONS.put("chin_scratch", "Chin scratch");
        ACTIONS.put("happy_purr", "Purr");
        ACTIONS.put("knead", "Make biscuits");
        ACTIONS.put("trill", "Trill");
        ACTIONS.put("meow", "Meow");
        ACTIONS.put("head_tilt", "Head tilt");
        ACTIONS.put("paw_tap", "Paw tap");
        ACTIONS.put("groom_paw", "Wash face");
        ACTIONS.put("groom_flank", "Groom");
        ACTIONS.put("yawn", "Yawn");
        ACTIONS.put("stretch_front", "Big stretch");
        ACTIONS.put("stretch_back", "Leg stretch");
        ACTIONS.put("stretch_long", "Long stretch");
        ACTIONS.put("roll_wiggle", "Roll over");
        ACTIONS.put("belly_reach", "Belly up reach");
        ACTIONS.put("upside_meow", "Upside-down meow");
        ACTIONS.put("over_shoulder", "Look back");
        ACTIONS.put("cheek_rub", "Cheek rub");
        ACTIONS.put("pounce", "Pounce");
        ACTIONS.put("sneeze", "Sneeze");
        ACTIONS.put("sunbathe", "Sunbathe");
        ACTIONS.put("sleep_blep", "Nap (with blep)");
        ACTIONS.put("sleep", "Nap");
        ACTIONS.put("sit", "Sit");
        ACTIONS.put("loaf", "Loaf");
        ACTIONS.put("sphinx", "Sphinx");
        ACTIONS.put("lie_side", "Lie down");
        ACTIONS.put("belly_up", "Belly up");
        ACTIONS.put("stand", "Stand");
    }

    private static final int CREAM = 0xFFF6EFE6;
    private static final int FAWN = 0xFFD9B48A;
    private static final int SEAL = 0xFF3A2C24;
    private static final int BLUE = 0xFF8EA2DD;
    private static final int PINK = 0xFFE8A3AE;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public NylahScreen() {
        super(Component.literal("Nylah"));
    }

    @Override
    protected void init() {
        Nylah n = Nylah.get();
        NylahConfig cfg = n.config();
        int cols = width >= 520 ? 4 : 3;
        int bw = 108;
        int bh = 18;
        int gap = 4;
        List<String> have = n.clipNames();
        long count = ACTIONS.keySet().stream().filter(have::contains).count();
        int rows = (int) ((count + cols - 1) / cols);
        panelW = cols * (bw + gap) - gap + 24;
        panelH = 34 + rows * (bh + gap) + 14 + 2 * (bh + gap) + 14;
        panelX = (width - panelW) / 2;
        panelY = Math.max(6, (height - panelH) / 2);

        int i = 0;
        int x0 = panelX + 12;
        int y0 = panelY + 30;
        for (Map.Entry<String, String> e : ACTIONS.entrySet()) {
            if (!have.contains(e.getKey())) {
                continue;
            }
            int x = x0 + (i % cols) * (bw + gap);
            int y = y0 + (i / cols) * (bh + gap);
            String clip = e.getKey();
            addRenderableWidget(new SoftButton(x, y, bw, bh, e.getValue(), BLUE, b -> {
                Nylah.get().perform(clip);
                onClose();
            }));
            i++;
        }
        int sy = y0 + rows * (bh + gap) + 14;
        int sw = (panelW - 24 - gap * 3) / 4;
        addRenderableWidget(new SoftButton(x0, sy, sw, bh, "Come here!", PINK, b -> {
            Nylah.get().come();
            onClose();
        }));
        addRenderableWidget(new SoftButton(x0 + (sw + gap), sy, sw, bh, cfg.napping ? "Wake up" : "Nap time", PINK, b -> {
            Nylah.get().toggleNap();
            onClose();
        }));
        addRenderableWidget(new SoftButton(x0 + 2 * (sw + gap), sy, sw, bh, "Detail: " + cfg.textureDetail + "x", FAWN, b -> {
            cfg.textureDetail = cfg.textureDetail == 2 ? 4 : cfg.textureDetail == 4 ? 8 : 2;
            cfg.save();
            rebuildWidgets();
        }));
        addRenderableWidget(new SoftButton(x0 + 3 * (sw + gap), sy, sw, bh, "Name: " + label(cfg.nameTag), FAWN, b -> {
            cfg.nameTag = NylahConfig.NameTag.values()[(cfg.nameTag.ordinal() + 1) % NylahConfig.NameTag.values().length];
            cfg.save();
            rebuildWidgets();
        }));
        int sy2 = sy + bh + gap;
        addRenderableWidget(new SoftButton(x0, sy2, sw, bh, "Sounds: " + Math.round(cfg.volume * 100) + "%", FAWN, b -> {
            float v = cfg.volume;
            cfg.volume = v >= 0.99f ? 0f : v < 0.25f ? 0.4f : v < 0.6f ? 0.8f : 1f;
            cfg.save();
            rebuildWidgets();
        }));
        addRenderableWidget(new SoftButton(x0 + (sw + gap), sy2, sw, bh, "Mood: " + cap(cfg.activity.name()), FAWN, b -> {
            cfg.activity = NylahConfig.Activity.values()[(cfg.activity.ordinal() + 1) % NylahConfig.Activity.values().length];
            cfg.save();
            rebuildWidgets();
        }));
        addRenderableWidget(new SoftButton(x0 + 2 * (sw + gap), sy2, sw, bh, "Something cute", PINK, b -> {
            Nylah.get().doSomethingCute();
            onClose();
        }));
        addRenderableWidget(new SoftButton(x0 + 3 * (sw + gap), sy2, sw, bh, "Done", BLUE, b -> onClose()));
    }

    private static String label(NylahConfig.NameTag t) {
        return switch (t) {
            case ALWAYS -> "always";
            case LOOKING -> "on look";
            case NEVER -> "hidden";
        };
    }

    private static String cap(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x90000000);
        int x2 = panelX + panelW;
        int y2 = panelY + panelH;
        g.fill(panelX + 1, panelY, x2 - 1, y2, CREAM);
        g.fill(panelX, panelY + 1, x2, y2 - 1, CREAM);
        g.fill(panelX + 1, panelY + 22, x2 - 1, panelY + 23, FAWN);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        var font = Minecraft.getInstance().font;
        int strokes = Nylah.get().config().strokes;
        g.text(font, "Nylah", panelX + 12, panelY + 8, SEAL, false);
        String sub = strokes > 0 ? "strokes: " + strokes + "  ♥" : "♥";
        g.text(font, sub, panelX + panelW - 12 - font.width(sub), panelY + 8, PINK, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A soft rounded-ish button in her colours. */
    private static final class SoftButton extends Button {

        private final String text;
        private final int accent;

        SoftButton(int x, int y, int w, int h, String text, int accent, OnPress onPress) {
            super(x, y, w, h, Component.literal(text), onPress, DEFAULT_NARRATION);
            this.text = text;
            this.accent = accent;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int x1 = getX();
            int y1 = getY();
            int x2 = x1 + getWidth();
            int y2 = y1 + getHeight();
            boolean hot = isHoveredOrFocused();
            int fill = hot ? accent : 0xFFFFFFFF;
            int edge = accent;
            g.fill(x1 + 1, y1, x2 - 1, y2, edge);
            g.fill(x1, y1 + 1, x2, y2 - 1, edge);
            g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
            var font = Minecraft.getInstance().font;
            int tx = x1 + (getWidth() - font.width(text)) / 2;
            g.text(font, text, tx, y1 + (getHeight() - 8) / 2, hot ? 0xFFFFFFFF : SEAL, false);
        }
    }
}
