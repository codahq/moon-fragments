package me.bendy.moonfragments.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Minimap icon for the falling moon fragment. <b>Never drawn in the world</b>, so don't delete it as unused:
 * {@link FallingMoonFragmentRenderer#getRenderType} returns {@code null}, so the renderer skips this model and draws the
 * glow, beam and fragment by hand. The model only has to be reachable through {@code LivingEntityRenderer.getModel()},
 * where minimap mods look for it:
 * <ul>
 *   <li><b>Xaero's Minimap</b> renders the whole hierarchy under the model's root.</li>
 *   <li><b>JourneyMap</b> projects a part named {@code "head"} onto the entity texture (then {@code "body"}, then
 *       {@code "root"}). That's why the plate is called <b>head</b>.</li>
 *   <li><b>VoxelMap</b> renders the {@code ModelPart} fields declared on the model class, which is why
 *       {@link #iconPlate} exists.</li>
 * </ul>
 *
 * <p><b>Geometry:</b> one flat 7x7 plate, one unit deep, centred on the entity origin, so it's seen face-on. It's
 * slightly smaller than a mob's 8x8 head, so the icon sits at mob-head size on the map. The size is odd so the dome has a
 * true centre column. At {@code texOffs(0, 0)} the front face samples u 1-8, v 1-8 and the back face u 9-16, v 1-8 of
 * {@code textures/entity/moon_fragment_icon.png}. Both faces carry the dome, so either side reads correctly. The sheet
 * size passed to {@link LayerDefinition#create} must equal the PNG's real size (16x16).
 */
public class MoonFragmentIconModel extends EntityModel<FallingMoonFragmentRenderer.State> {
    /** Declared here, not inherited, so VoxelMap's reflective field scan finds it. Rendering it draws the plate. */
    @SuppressWarnings("unused")
    private final ModelPart iconPlate;

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -3.5F, -0.5F, 7, 7, 1),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16); // must match moon_fragment_icon.png
    }

    public MoonFragmentIconModel(ModelPart root) {
        super(root);
        this.iconPlate = root;
    }

    /** Static icon: nothing to pose, and no need for the base class's per-frame reset. */
    @Override
    public void setupAnim(FallingMoonFragmentRenderer.State state) {
    }
}
