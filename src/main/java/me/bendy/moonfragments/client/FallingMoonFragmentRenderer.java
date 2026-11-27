package me.bendy.moonfragments.client;

import me.bendy.moonfragments.MoonFragments;
import me.bendy.moonfragments.entity.FallingMoonFragment;
import me.bendy.moonfragments.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

/**
 * Draws the falling light, the beacon and the fragment by hand. It's a {@link LivingEntityRenderer} only so minimap mods
 * can find {@link MoonFragmentIconModel} through {@code getModel()}. The model itself is never drawn (see
 * {@link #getRenderType}).
 */
public class FallingMoonFragmentRenderer
        extends LivingEntityRenderer<FallingMoonFragment, FallingMoonFragmentRenderer.State, MoonFragmentIconModel> {
    private static final Identifier GLOW_TEXTURE = Identifier.fromNamespaceAndPath(MoonFragments.MODID, "textures/entity/moon_glow.png");
    /** The sheet minimap mods texture the icon plate with. */
    private static final Identifier ICON_TEXTURE = Identifier.fromNamespaceAndPath(MoonFragments.MODID, "textures/entity/moon_fragment_icon.png");
    private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(GLOW_TEXTURE);
    /** Pale blue-white, matching the fragment colour. */
    private static final int GLOW_COLOR = 0xFFE6F0FF;
    private static final int BEAM_COLOR = 0xFFFFFFFF;
    private static final int BEAM_HEIGHT = 1024;

    private final ItemModelResolver itemModelResolver;
    private final ItemStack fragmentStack = new ItemStack(ModItems.MOON_FRAGMENT.get());

    public FallingMoonFragmentRenderer(EntityRendererProvider.Context context) {
        // Baked inline: the icon model never animates or reloads, it only has to exist for minimap mods.
        super(context, new MoonFragmentIconModel(MoonFragmentIconModel.createLayer().bakeRoot()), 0.15F);
        this.itemModelResolver = context.getItemModelResolver();
    }

    public static class State extends LivingEntityRenderState {
        public FallingMoonFragment.Stage stage = FallingMoonFragment.Stage.FALLING;
        /** Yaw the fragment rolls along ({@code yRot} on a living render state is the head's yaw). */
        public float facing;
        /** Current displayed tilt in degrees, including the reveal roll. */
        public float tilt;
        public final ItemStackRenderState item = new ItemStackRenderState();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FallingMoonFragment entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.stage = entity.getStage();
        state.facing = entity.getYRot(partialTick);
        // Roll end over end (one full turn, easing out) and come to rest at the entity's chosen tilt.
        float progress = entity.getRollProgress(partialTick);
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        state.tilt = entity.getTilt() + (1.0F - eased) * 360.0F;
        if (state.stage == FallingMoonFragment.Stage.FRAGMENT) {
            itemModelResolver.updateForNonLiving(state.item, fragmentStack, ItemDisplayContext.GROUND, entity);
        } else {
            state.item.clear();
        }
    }

    /** Skips the icon model in the world; the living-entity pass still runs, so name tags and leashes work. */
    @Override
    protected @Nullable RenderType getRenderType(State state, boolean visible, boolean translucent, boolean glowing) {
        return null;
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return ICON_TEXTURE;
    }

    /** Only a custom name shows, as on mobs: {@code LivingEntityRenderer} alone would label every fragment. */
    @Override
    protected boolean shouldShowName(FallingMoonFragment entity, double distanceToCameraSq) {
        return super.shouldShowName(entity, distanceToCameraSq)
                && (entity.shouldShowName() || entity.hasCustomName() && entity == this.entityRenderDispatcher.crosshairPickEntity);
    }

    /** The fragment is its own light: always full-bright. */
    @Override
    protected int getBlockLightLevel(FallingMoonFragment entity, BlockPos pos) {
        return 15;
    }

    /** The beam reaches far above the entity's box, so never frustum-cull on the box alone. */
    @Override
    protected boolean affectedByCulling(FallingMoonFragment entity) {
        return false;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        switch (state.stage) {
            case FALLING -> submitGlow(state, poseStack, collector, camera, 1.6F, 0.25F);
            case BEACON -> {
                float pulse = 1.0F + Mth.sin(state.ageInTicks * 0.15F) * 0.1F;
                submitGlow(state, poseStack, collector, camera, 1.2F * pulse, 0.3F);
                poseStack.pushPose();
                poseStack.translate(-0.5F, 0.3F, -0.5F); // submitBeaconBeam centres on +0.5
                BeaconRenderer.submitBeaconBeam(poseStack, collector, BeaconRenderer.BEAM_LOCATION, 1.0F, state.ageInTicks,
                        0, BEAM_HEIGHT, BEAM_COLOR, 0.15F, 0.2F);
                poseStack.popPose();
            }
            case FRAGMENT -> {
                if (!state.item.isEmpty()) {
                    // Pivot on the centre of the flat face, tilt, then lift so the lowest point touches the ground.
                    AABB box = state.item.getModelBoundingBox();
                    poseStack.pushPose();
                    poseStack.translate(0.0F, restingLift(box, state.tilt), 0.0F);
                    poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing));
                    poseStack.mulPose(Axis.XP.rotationDegrees(state.tilt));
                    poseStack.translate(0.0F, (float) -box.minY, 0.0F);
                    state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
                    poseStack.popPose();
                }
                submitGlow(state, poseStack, collector, camera, 0.7F, 0.2F);
            }
        }
        super.submit(state, poseStack, collector, camera);
    }

    /**
     * Height to raise the flat-face centre so a half-dome (radius r, height h) tilted by {@code tiltDegrees} rests on
     * the ground: on its rim while the dome still points up-ish, on its curved surface once tipped past 90°.
     */
    private static float restingLift(AABB box, float tiltDegrees) {
        float r = (float) Math.max(box.getXsize(), box.getZsize()) * 0.5F;
        float h = (float) box.getYsize();
        float t = Math.abs(Mth.wrapDegrees(tiltDegrees)) * Mth.DEG_TO_RAD; // 0..pi, symmetric
        float sin = Mth.sin(t);
        float cos = Mth.cos(t);
        if (t <= Mth.HALF_PI) {
            return r * sin;
        }
        return Mth.sqrt(h * cos * h * cos + r * sin * r * sin);
    }

    /** A camera-facing soft glow quad centred {@code yOffset} above the entity origin. */
    private static void submitGlow(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
            float size, float yOffset) {
        poseStack.pushPose();
        poseStack.translate(0.0F, yOffset, 0.0F);
        poseStack.mulPose(camera.orientation);
        poseStack.scale(size, size, size);
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, GLOW, (pose, consumer) -> {
            vertex(consumer, pose, light, -0.5F, -0.5F, 0, 1);
            vertex(consumer, pose, light, 0.5F, -0.5F, 1, 1);
            vertex(consumer, pose, light, 0.5F, 0.5F, 1, 0);
            vertex(consumer, pose, light, -0.5F, 0.5F, 0, 0);
        });
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light, float x, float y, int u, int v) {
        consumer.addVertex(pose, x, y, 0.0F)
                .setColor(GLOW_COLOR)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
