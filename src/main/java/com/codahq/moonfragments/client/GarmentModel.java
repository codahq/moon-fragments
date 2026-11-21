package com.codahq.moonfragments.client;

import java.util.List;
import java.util.Map;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * Worn models for leg-slot garments that replace the leggings shape (dress, mini skirt).
 * <p>
 * The fixed parts (bodice, sleeves, waistband) are ordinary boxes. The skirt below the hip is a {@link SkirtMesh}: one
 * connected surface whose front follows the forward leg, whose back follows the rear leg, and whose sides fan between
 * them, so the legs stay inside and the skirt never splits. It copies the body's pose, so it also follows crouching.
 */
public class GarmentModel extends HumanoidModel<HumanoidRenderState> {
    /** A garment: its fixed boxes and its bendable skirt. */
    public record Garment(LayerDefinition layer, SkirtMesh.Spec skirt) {
    }

    private final SkirtMesh skirt;

    private GarmentModel(ModelPart root, SkirtMesh skirt) {
        super(root);
        this.skirt = skirt;
    }

    /** Bakes the fixed parts and adds the skirt mesh as an extra top-level part that mirrors the body's pose. */
    public static GarmentModel create(Garment garment) {
        ModelPart baked = garment.layer().bakeRoot();
        SkirtMesh skirt = new SkirtMesh(garment.skirt());
        ModelPart root = new ModelPart(List.of(), Map.of(
                "head", baked.getChild("head"),
                "body", baked.getChild("body"),
                "right_arm", baked.getChild("right_arm"),
                "left_arm", baked.getChild("left_arm"),
                "right_leg", baked.getChild("right_leg"),
                "left_leg", baked.getChild("left_leg"),
                "skirt", skirt.part()));
        return new GarmentModel(root, skirt);
    }

    @Override
    public void setupAnim(HumanoidRenderState state) {
        super.setupAnim(state); // poses the legs and body
        skirt.part().loadPose(body.storePose());
        // Negative xRot swings a leg forward, positive swings it back.
        float front = Math.min(0.0F, Math.min(rightLeg.xRot, leftLeg.xRot));
        float back = Math.max(0.0F, Math.max(rightLeg.xRot, leftLeg.xRot));
        skirt.update(front, back);
    }

    /**
     * Dress: fitted bodice, short puffed sleeves, and a three-tier flared skirt to just below the knee.
     * Texture 64x64 at {@code humanoid_leggings/moon_fragment_dress.png}. UVs: bodice (0,0), sleeve (48,0) (mirrored on
     * the left arm), skirt tiers as box layouts at (0,16) 11x6x7, (0,29) 13x4x9, (0,42) 15x3x11.
     */
    public static Garment createDress() {
        MeshDefinition mesh = baseMesh();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(48, 0).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.6F)),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(48, 0).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.6F)),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.5F)), PartPose.ZERO);
        // Starts at y 10 and is a little wider/deeper than hip-high boots (x ±4.9, z ±3, tops at y ~11) to hide their tops.
        // The start ring matches the bodice's outline (x ±4.5, z ±2.5), so a ledge closes the top: no gap seen from above.
        SkirtMesh.Spec skirt = new SkirtMesh.Spec(4.5F, 2.5F, List.of(
                new SkirtMesh.Band(10, 16, 5.5F, 3.5F, 0, 0, 16),
                new SkirtMesh.Band(16, 20, 6.5F, 4.5F, 0, 0, 29),
                new SkirtMesh.Band(20, 23, 7.5F, 5.5F, 0, 0, 42)),
                7, 6, 64, 64);
        return new Garment(LayerDefinition.create(mesh, 64, 64), skirt);
    }

    /**
     * Mini skirt: a high waistband, a fixed upper tier over the hips, and a pleated, strongly flared hem ending at the
     * top of the thigh, clear of hip-high boots (which reach about x ±4.9, z ±3). The pleats are a zig-zag outline.
     * Texture 64x32 at {@code humanoid_leggings/moon_fragment_skirt.png}. UVs: waistband (0,0) 8x2x4, upper tier (0,6)
     * 11x2x7, hem as a box layout at (0,15) 13x2x9.
     */
    public static Garment createSkirt() {
        MeshDefinition mesh = baseMesh();
        mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, 8.0F, -2.0F, 8.0F, 2.0F, 4.0F, new CubeDeformation(0.6F))   // band, y ~7.4-10.6
                // Upper tier, y 10-12: a little wider/deeper than hip-high boots so it covers their tops.
                .texOffs(0, 6).addBox(-5.5F, 10.0F, -3.5F, 11.0F, 2.0F, 7.0F),
                PartPose.ZERO);
        SkirtMesh.Spec skirt = new SkirtMesh.Spec(5.5F, 3.5F, List.of(
                new SkirtMesh.Band(12, 14, 6.5F, 4.5F, 0.5F, 0, 15)),
                13, 9, 64, 32);
        return new Garment(LayerDefinition.create(mesh, 64, 32), skirt);
    }

    /** A humanoid mesh with every part empty; garments replace the parts they use. */
    private static MeshDefinition baseMesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder empty = CubeListBuilder.create();
        PartDefinition head = root.addOrReplaceChild("head", empty, PartPose.ZERO);
        head.addOrReplaceChild("hat", empty, PartPose.ZERO);
        root.addOrReplaceChild("body", empty, PartPose.ZERO);
        root.addOrReplaceChild("right_arm", empty, PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", empty, PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", empty, PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", empty, PartPose.offset(1.9F, 12.0F, 0.0F));
        return mesh;
    }
}
