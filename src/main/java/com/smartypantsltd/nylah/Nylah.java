package com.smartypantsltd.nylah;

import com.smartypantsltd.nylah.anim.Animator;
import com.smartypantsltd.nylah.anim.Clip;
import com.smartypantsltd.nylah.anim.ClipLibrary;
import com.smartypantsltd.nylah.anim.Rig;
import com.smartypantsltd.nylah.entity.NylahCat;
import com.smartypantsltd.nylah.model.Geo;
import com.smartypantsltd.nylah.move.LevelGrid;
import com.smartypantsltd.nylah.move.Mover;
import com.smartypantsltd.nylah.move.PathFinder;
import com.smartypantsltd.nylah.render.NylahRenderer;
import com.smartypantsltd.nylah.ui.NylahScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * The hub: owns Nylah, and runs her once per client tick.
 *
 * <p>Each tick: make sure she is in the world (she is simply always there),
 * gather what she can sense, let her brain (or the debug reel) decide, walk
 * her, animate her, play her sounds. Nothing here sends anything to the
 * server: no packets, no chat, no commands. Every message Nylah shows you is a
 * client-side chat line only you can see.</p>
 */
public final class Nylah {

    public static final Logger LOG = LoggerFactory.getLogger("Nylah");

    /** Her eye blue, for her name and her messages. */
    public static final int EYE_BLUE = 0x8EA2DD;
    /** Deep-negative fake entity ids, counting down: nowhere near server ids. */
    private static final int ID_BASE = -1_900_000_000;

    private static Nylah instance;

    private final NylahConfig config;
    private final Geo geo;
    private final Rig rig;
    private final Map<String, Clip> clips;
    private final Voice voice = new Voice();
    private final DebugReel debug = new DebugReel();

    private NylahRenderer renderer;

    private NylahCat cat;
    private Animator animator;
    private Mover mover;
    private Brain brain;
    private ClientLevel lastLevel;
    private int nextId = ID_BASE;
    private int settleTicks;

    private Vec3 lastPlayerPos;
    private int stillTicks;
    private int lookingTicks;
    private int offGroundTicks;
    private long lastSwing = -1000;
    private long ticks;
    private boolean lookingAtHer;
    private int strokeCooldown;

    private Nylah() {
        config = NylahConfig.load();
        geo = Geo.load();
        rig = geo.rig();
        clips = ClipLibrary.load(rig);
        LOG.info("Nylah ready: {} bones, {} animations", rig.size(), clips.size());
    }

    public static void init() {
        if (instance == null) {
            instance = new Nylah();
        }
    }

    public static Nylah get() {
        init();
        return instance;
    }

    public NylahConfig config() {
        return config;
    }

    public NylahCat cat() {
        return cat;
    }

    public List<String> clipNames() {
        return List.copyOf(clips.keySet());
    }

    // ------------------------------------------------------------------ rendering hooks

    /** Called (by mixin) whenever vanilla builds its entity renderers: build hers too. */
    public void onRendererContext(EntityRendererProvider.Context ctx) {
        try {
            NylahRenderer.registerCoats(Minecraft.getInstance().getTextureManager());
            renderer = new NylahRenderer(ctx, geo, rig);
        } catch (Throwable t) {
            LOG.error("Could not build Nylah's renderer", t);
            renderer = null;
        }
    }

    public NylahRenderer renderer() {
        return renderer;
    }

    public boolean showNameTag() {
        return switch (config.nameTag) {
            case ALWAYS -> true;
            case LOOKING -> lookingAtHer;
            case NEVER -> false;
        };
    }

    public Component nameTag() {
        return Component.literal("Nylah").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(EYE_BLUE)));
    }

    public String debugLabel() {
        return debug.label();
    }

    // ------------------------------------------------------------------ the tick

    public void tick(Minecraft mc) {
        ticks++;
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            forget();
            lastLevel = null;
            return;
        }
        keys(mc);
        if (mc.isPaused()) {
            return;
        }

        boolean wanted = config.enabled && !config.napping;
        boolean newLevel = level != lastLevel;
        boolean freshJoin = lastLevel == null;
        lastLevel = level;
        if (newLevel) {
            forget();
            settleTicks = 0;
        }

        if (!wanted) {
            if (cat != null) {
                if (brain.mode() != Brain.Mode.LEAVE) {
                    brain.leave();
                }
            }
        }
        if (cat == null && wanted) {
            // Give the world a moment to load around you before she appears.
            if (++settleTicks < 40 || !player.onGround()) {
                return;
            }
            spawn(mc, level, player, freshJoin);
        }
        if (cat == null) {
            return;
        }
        if (cat.isRemoved() || cat.level() != level || level.getEntity(cat.getId()) != cat) {
            // Something replaced her (a real entity took her id?): quietly come back.
            forget();
            return;
        }

        Senses s = senses(mc, player);
        double dist = cat.position().distanceTo(s.player());
        if (wanted && !debug.active() && (dist > 26 || (mover.stuck() && dist > 6))) {
            catchUp(mc, level, player);
        }

        if (debug.active()) {
            if (debug.tick(cat, animator, mover, s)) {
                say("debug done: played all " + debug.size() + " animations.");
            }
        } else {
            brain.tick(s, cat, animator, mover, config);
            animator.setBlep(brain.blepWanted());
            mover.tick(cat, brain.wantSpeed());
            animator.setMotion(mover.speed(), mover.airborne());
            if (brain.finishedLeaving()) {
                remove();
                return;
            }
        }
        animator.tick();
        for (String e = animator.pollEvent(); e != null; e = animator.pollEvent()) {
            voice.play(cat, e, config.volume);
        }
        if (animator.purring() && ticks % 40 == 0) {
            voice.play(cat, "purr", config.volume);
        }
        voice.tick();
        if (strokeCooldown > 0) {
            strokeCooldown--;
        }
    }

    private Senses senses(Minecraft mc, LocalPlayer p) {
        Vec3 pos = p.position();
        double speed = lastPlayerPos == null ? 0 : Math.hypot(pos.x - lastPlayerPos.x, pos.z - lastPlayerPos.z);
        stillTicks = speed < 0.01 ? stillTicks + 1 : 0;
        lastPlayerPos = pos;
        offGroundTicks = p.onGround() ? 0 : offGroundTicks + 1;
        boolean airborne = p.getAbilities().flying || p.isFallFlying() || p.isPassenger()
                || (p.isInWater() && !p.onGround()) || offGroundTicks > 25;
        if (p.swinging && p.getMainHandItem().typeHolder().is(ItemTags.PICKAXES)) {
            lastSwing = ticks;
        }
        lookingAtHer = looksAt(p, 12.0, 0.35);
        lookingTicks = lookingAtHer ? lookingTicks + 1 : 0;
        ClientLevel level = mc.level;
        return new Senses(pos, p.getEyePosition(), p.getYRot(), speed, stillTicks, airborne, p.isShiftKeyDown(),
                p.isSleeping(), ticks - lastSwing < 60, lookingAtHer, lookingTicks,
                level.isDarkOutside(), level.isRaining());
    }

    private boolean looksAt(LocalPlayer p, double range, double inflate) {
        if (cat == null) {
            return false;
        }
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        AABB box = cat.getBoundingBox().inflate(inflate);
        return box.clip(eye, end).isPresent();
    }

    // ------------------------------------------------------------------ presence

    private void spawn(Minecraft mc, ClientLevel level, LocalPlayer player, boolean freshJoin) {
        animator = new Animator(rig, clips);
        mover = new Mover(new LevelGrid(level));
        brain = new Brain();
        NylahCat c = new NylahCat(level, animator);
        int id = nextId--;
        while (level.getEntity(id) != null) {
            id = nextId--;
        }
        c.setId(id);
        c.setCustomName(Component.literal("Nylah"));
        Vec3 at;
        float yaw;
        if (freshJoin && config.firstMeetingDone) {
            // Joining a world: she is already sitting beside you.
            at = besideYou(player);
            yaw = player.getYRot() + 180;
            Mover.teleport(c, at, yaw);
            level.addEntity(c);
            cat = c;
            brain.settled(animator);
            return;
        }
        // Otherwise she arrives: from out of view, padding in to you.
        Vec3 hidden = hiddenSpot(mc, level, player);
        Vec3 d = player.position().subtract(hidden);
        yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        Mover.teleport(c, hidden, yaw);
        level.addEntity(c);
        cat = c;
        boolean greet = !config.firstMeetingDone;
        brain.arrive(greet);
        if (greet) {
            config.firstMeetingDone = true;
            config.save();
        }
    }

    private void catchUp(Minecraft mc, ClientLevel level, LocalPlayer player) {
        Vec3 hidden = hiddenSpot(mc, level, player);
        Vec3 d = player.position().subtract(hidden);
        Mover.teleport(cat, hidden, (float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        mover.stop();
        animator.stopAction();
        brain.arrive(false);
    }

    private Vec3 besideYou(LocalPlayer p) {
        double ang = Math.toRadians(p.getYRot() + 60);
        Vec3 want = p.position().add(-Math.sin(ang) * 1.3, 0, Math.cos(ang) * 1.3);
        double f = mover.grid().feet((int) Math.floor(want.x), (int) Math.floor(p.getY() + 0.01), (int) Math.floor(want.z));
        return Double.isNaN(f) ? p.position() : new Vec3(want.x, f, want.z);
    }

    /** Somewhere behind the camera she can walk to you from; your heel if nothing better. */
    private Vec3 hiddenSpot(Minecraft mc, ClientLevel level, LocalPlayer p) {
        float camYaw = mc.gameRenderer.getMainCamera().yRot();
        double fov = mc.options.fov().get();
        int py = (int) Math.floor(p.getY() + 0.01);
        PathFinder finder = mover.finder();
        for (double[] off : SpawnSpots.candidates(camYaw, new double[] {7.5, 5.5, 9.5, 4})) {
            if (!SpawnSpots.outOfView(camYaw, off[0], off[1], fov)) {
                continue;
            }
            int x = (int) Math.floor(p.getX() + off[0]);
            int z = (int) Math.floor(p.getZ() + off[1]);
            double f = mover.grid().feet(x, py, z);
            if (Double.isNaN(f) || Math.abs(f - p.getY()) > 3) {
                continue;
            }
            List<PathFinder.Point> path = finder.find(x, f, z, (int) Math.floor(p.getX()), py, (int) Math.floor(p.getZ()));
            if (path.isEmpty()) {
                continue;
            }
            PathFinder.Point last = path.get(path.size() - 1);
            if (Math.hypot(last.x() - p.getX(), last.z() - p.getZ()) < 2.0) {
                return new Vec3(x + 0.5, f, z + 0.5);
            }
        }
        double ang = Math.toRadians(camYaw + 180);
        return p.position().add(-Math.sin(ang) * 1.5, 0, Math.cos(ang) * 1.5);
    }

    private void forget() {
        if (cat != null && !cat.isRemoved()) {
            cat.discard();
        }
        cat = null;
        if (debug.active() && animator != null) {
            debug.stop(animator);
        }
    }

    private void remove() {
        if (cat != null) {
            cat.discard();
        }
        cat = null;
    }

    // ------------------------------------------------------------------ input

    private void keys(Minecraft mc) {
        while (NylahKeybinds.MENU.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new NylahScreen());
            }
        }
        while (NylahKeybinds.COME.consumeClick()) {
            come();
        }
        while (NylahKeybinds.CUTE.consumeClick()) {
            doSomethingCute();
        }
        while (NylahKeybinds.NAP.consumeClick()) {
            toggleNap();
        }
    }

    public void come() {
        if (config.napping) {
            toggleNap();
            return;
        }
        if (brain != null && !debug.active()) {
            brain.come();
        }
    }

    public void perform(String action) {
        if (brain != null && !debug.active() && clips.containsKey(action)) {
            brain.perform(action);
        }
    }

    public void doSomethingCute() {
        String[] cute = {"look_up", "slow_blink", "blep", "head_tilt", "trill", "roll_wiggle", "knead", "chin_scratch", "belly_reach"};
        perform(cute[(int) (Math.random() * cute.length)]);
    }

    public void toggleNap() {
        config.napping = !config.napping;
        config.save();
        say(config.napping ? "is off for a nap. Call her back any time." : "is on her way!");
    }

    /**
     * A right-click: if it is a stroke on her, react and swallow the click.
     * Called from the Minecraft mixin; returns true to cancel the vanilla use.
     */
    public boolean onUse(Minecraft mc) {
        if (cat == null || debug.active()) {
            return false;
        }
        Gesture.Region region = Gesture.evaluate(mc, cat);
        if (region == null) {
            return false;
        }
        if (strokeCooldown == 0) {
            brain.stroked(region, animator, mover);
            config.strokes++;
            if (config.strokes % 10 == 0) {
                config.save();
            }
            strokeCooldown = 8;
            Vec3 p = cat.position();
            for (int i = 0; i < 2; i++) {
                cat.level().addParticle(ParticleTypes.HEART, p.x + (Math.random() - 0.5) * 0.4, p.y + 0.6 + Math.random() * 0.2,
                        p.z + (Math.random() - 0.5) * 0.4, 0, 0.05, 0);
            }
            voice.play(cat, "purr", config.volume);
        }
        return true;
    }

    // ------------------------------------------------------------------ /nylah

    /** A command typed in chat. Returns true when it was ours (it is then never sent to the server). */
    public boolean onCommand(String command) {
        String c = command.strip();
        if (!c.equals("nylah") && !c.startsWith("nylah ")) {
            return false;
        }
        String arg = c.length() > 5 ? c.substring(5).strip() : "";
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> runCommand(mc, arg));
        return true;
    }

    private void runCommand(Minecraft mc, String arg) {
        switch (arg) {
            case "debug" -> {
                if (cat == null || mc.player == null) {
                    say("isn't here right now (is she napping?).");
                    return;
                }
                if (debug.active()) {
                    say("is already showing you everything. /nylah debug stop to end it.");
                    return;
                }
                mover.stop();
                debug.start(animator, senses(mc, mc.player));
                say("debug: playing all " + debug.size() + " animations, about " + debug.seconds()
                        + " seconds. Watch her! (/nylah debug stop to end early)");
            }
            case "debug stop" -> {
                if (debug.active()) {
                    debug.stop(animator);
                    say("debug done: stopped early.");
                }
            }
            case "come" -> come();
            case "nap" -> toggleNap();
            default -> mc.setScreen(new NylahScreen());
        }
    }

    /** A chat line only you can see, from Nylah. Never sent anywhere. */
    public void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        MutableComponent name = Component.literal("Nylah ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(EYE_BLUE)));
        MutableComponent body = Component.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xE9E3EF)));
        mc.execute(() -> mc.gui.getChat().addClientSystemMessage(name.append(body)));
    }
}
