package com.deveisia.becmod;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.event.PostMaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconType;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.fluids.FluidBuilder;
import com.gregtechceu.gtceu.api.fluids.FluidState;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKey;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BecFluids {
    public static final String LANG_KEY = "becmod.fluid.bose_einstein_condensated";
    public static final String REGISTRY_PREFIX = "bec_";

    public static final FluidStorageKey BEC = new FluidStorageKey(
            BecMod.id("bose_einstein_condensate"),
            MaterialIconType.liquid,
            m -> REGISTRY_PREFIX + m.getName(),
            m -> LANG_KEY,
            FluidState.LIQUID,
            -10);

    private static final List<FluidStorageKey> SPECIAL_KEYS = List.of(
            FluidStorageKeys.MOLTEN, FluidStorageKeys.GAS, FluidStorageKeys.PLASMA);

    private static final List<Material> REGISTERED = new ArrayList<>();

    private BecFluids() {}

    public static void init() {
    }

    public static List<Material> registeredMaterials() {
        return Collections.unmodifiableList(REGISTERED);
    }

    public static void onPostMaterial(PostMaterialEvent event) {
        if (BecConfig.whitelist().isEmpty()) {
            BecMod.LOGGER.info("Whitelist is empty; no condensate fluids registered");
            return;
        }
        for (String entry : BecConfig.whitelist()) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id == null) {
                BecMod.LOGGER.warn("Skipping '{}': not a valid material id", entry);
                continue;
            }
            Material material = findMaterial(id);
            if (material == null) {
                BecMod.LOGGER.warn("Skipping '{}': no such material", entry);
                continue;
            }
            tryRegister(material);
        }
        BecMod.LOGGER.info("Queued {} condensate fluid(s)", REGISTERED.size());
    }

    private static void tryRegister(Material material) {
        FluidProperty property = material.getProperty(PropertyKey.FLUID);
        if (property == null) {
            BecMod.LOGGER.warn("Skipping {}: material has no fluid", material.getResourceLocation());
            return;
        }
        if (property.getQueuedBuilder(BEC) != null) {
            return;
        }

        if (property.getQueuedBuilder(FluidStorageKeys.LIQUID) == null) {
            BecMod.LOGGER.warn("Skipping {}: it has no plain liquid, only special fluid(s) {}",
                    material.getResourceLocation(), describeSpecialKeys(property));
            return;
        }

        FluidBuilder builder = new FluidBuilder()
                .state(FluidState.LIQUID)
                .temperature(BecConfig.temperature())

                .customStill();

        property.enqueueRegistration(BEC, builder);
        REGISTERED.add(material);
        BecMod.LOGGER.debug("Queued condensate for {}", material.getResourceLocation());
    }

    private static String describeSpecialKeys(FluidProperty property) {
        List<String> found = new ArrayList<>();
        for (FluidStorageKey key : SPECIAL_KEYS) {
            if (property.getQueuedBuilder(key) != null) found.add(key.getResourceLocation().getPath());
        }
        return found.isEmpty() ? "[none queued]" : found.toString();
    }

    private static Material findMaterial(ResourceLocation id) {
        for (Material material : GTCEuAPI.materialManager.getRegisteredMaterials()) {
            if (id.equals(material.getResourceLocation())) return material;
        }
        return null;
    }
}
