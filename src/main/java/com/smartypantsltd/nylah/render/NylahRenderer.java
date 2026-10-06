package com.smartypantsltd.nylah.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.smartypantsltd.nylah.Nylah;
import com.smartypantsltd.nylah.anim.Pose;
import com.smartypantsltd.nylah.anim.Rig;
import com.smartypantsltd.nylah.entity.NylahCat;
import com.smartypantsltd.nylah.model.Geo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Draws Nylah from her own model and texture.
 *
 * <p>Not registered with vanilla's renderer table (she has no entity type of her
 * own): {@code EntityRenderDispatcherMixin} hands this renderer out for her
 * entity and her render state, and {@code EntityRenderersMixin} rebuilds it on
 * every resource reload, exactly when vanilla rebuilds its own.</p>
 */
public final class NylahRenderer extends MobRenderer<NylahCat, NylahRenderState, NylahModel> {

    /** Her model is authored large for detail; this brings her to cat size in the world. */
    public static final float MODEL_SCALE = 0.55f;

    /**
     * Her coat at 2x / 4x / 8x. These ids are REGISTERED textures (see
     * {@link #registerCoats}), loaded straight from our jar: a loader-only mod
     * cannot count on its assets being visible to the resource manager, and
     * this way no resource pack can repaint her either.
     */
    private static final Identifier[] TEXTURES = {
        Identifier.fromNamespaceAndPath("nylah", "coat_2x"),
        Identifier.fromNamespaceAndPath("nylah", "coat_4x"),
        Identifier.fromNamespaceAndPath("nylah", "coat_8x"),
    };
    private static final String[] FILES = {"nylah_2x.png", "nylah_4x.png", "nylah_8x.png"};

    /** Load her three coats from the jar into the texture manager (render thread, on each reload). */
    public static void registerCoats(net.minecraft.client.renderer.texture.TextureManager tm) {
        for (int i = 0; i < TEXTURES.length; i++) {
            String path = "/assets/nylah/textures/entity/" + FILES[i];
            try (java.io.InputStream in = NylahRenderer.class.getResourceAsStream(path)) {
                if (in == null) {
                    Nylah.LOG.error("{} missing from the jar", path);
                    continue;
                }
                com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(in);
                String label = "nylah " + FILES[i];
                tm.register(TEXTURES[i], new net.minecraft.client.renderer.texture.DynamicTexture(() -> label, img));
            } catch (Exception e) {
                Nylah.LOG.error("Could not load " + path, e);
            }
        }
    }

    private final Rig rig;

    public NylahRenderer(EntityRendererProvider.Context ctx, Geo geo, Rig rig) {
        super(ctx, new NylahModel(geo.bake(), geo, rig), 0.22f);
        this.rig = rig;
    }

    @Override
    public NylahRenderState createRenderState() {
        NylahRenderState s = new NylahRenderState();
        s.pose = new Pose(rig);
        return s;
    }

    @Override
    public void extractRenderState(NylahCat cat, NylahRenderState state, float partialTick) {
        super.extractRenderState(cat, state, partialTick);
        cat.animator().sample(partialTick, state.pose);
        Nylah n = Nylah.get();
        state.texture = TEXTURES[switch (n.config().textureDetail) {
            case 8 -> 2;
            case 4 -> 1;
            default -> 0;
        }];
        // Her name, per the setting; and never the server's below-name scoreboard
        // line (in 26.1 that is drawn under every named entity).
        // Vanilla only sets the name's anchor point (nameTagAttachment) when ITS
        // rule says show a name, and draws nothing without one (javap-verified),
        // so setting nameTag alone shows nothing: set the anchor too.
        if (n.showNameTag()) {
            state.nameTag = n.nameTag();
            if (state.nameTagAttachment == null) {
                state.nameTagAttachment = cat.getAttachments()
                        .getNullable(net.minecraft.world.entity.EntityAttachment.NAME_TAG, 0, cat.getYRot(partialTick));
            }
        } else {
            state.nameTag = null;
        }
        String debug = n.debugLabel();
        state.scoreText = debug == null ? null : Component.literal(debug);
    }

    @Override
    public Identifier getTextureLocation(NylahRenderState state) {
        return state.texture == null ? TEXTURES[0] : state.texture;
    }

    @Override
    protected void scale(NylahRenderState state, PoseStack poseStack) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }
}
