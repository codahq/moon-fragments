package me.bendy.moonfragments.client;

import java.util.function.Supplier;

import net.minecraft.client.model.Model;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Swaps the leggings model for a {@link GarmentModel} (dress, mini skirt) when the item is worn. */
public final class GarmentClientExtensions implements IClientItemExtensions {
    private final Supplier<GarmentModel.Garment> garment;
    private GarmentModel model;

    public GarmentClientExtensions(Supplier<GarmentModel.Garment> garment) {
        this.garment = garment;
    }

    @Override
    @SuppressWarnings("rawtypes") // NeoForge's hook uses the raw Model type
    public Model getHumanoidArmorModel(ItemStack itemStack, EquipmentClientInfo.LayerType layerType, Model original) {
        if (model == null) {
            model = GarmentModel.create(garment.get());
        }
        return model;
    }
}
