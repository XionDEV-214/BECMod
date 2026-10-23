package com.deveisia.becmod.client;

import com.deveisia.becmod.BecConfig;
import com.deveisia.becmod.BecFluids;
import com.deveisia.becmod.BecMod;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorage;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;
import com.gregtechceu.gtceu.data.pack.GTDynamicResourcePack;
import com.gregtechceu.gtceu.data.pack.event.RegisterDynamicResourcesEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BecTextureGenerator {
    private BecTextureGenerator() {}

    public static void onRegisterDynamicResources(RegisterDynamicResourcesEvent event) {
        List<Material> materials = BecFluids.registeredMaterials();
        Map<ResourceLocation, BecSplitRender.Layers> layers = new HashMap<>();
        if (materials.isEmpty()) {
            BecSplitRender.setLayers(layers);
            return;
        }

        long start = System.currentTimeMillis();
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        int done = 0;
        try (AnimatedTexture overlay = AnimatedTexture.load(rm, BecSplitRender.OVERLAY)) {
            for (Material material : materials) {
                try {
                    if (generate(rm, material, overlay, layers)) done++;
                } catch (Exception e) {
                    BecMod.LOGGER.error("Failed to build condensate texture for {}",
                            material.getResourceLocation(), e);
                }
            }
        } catch (Exception e) {
            BecMod.LOGGER.error("Could not load overlay texture {}", BecSplitRender.OVERLAY, e);
        }
        BecSplitRender.setLayers(layers);
        BecMod.LOGGER.info("Built {} condensate texture(s) in {}ms", done, System.currentTimeMillis() - start);
    }

    private static boolean generate(ResourceManager rm, Material material, AnimatedTexture overlay,
                                    Map<ResourceLocation, BecSplitRender.Layers> layers) throws Exception {
        FluidProperty property = material.getProperty(PropertyKey.FLUID);
        if (property == null) return false;
        Fluid liquid = property.get(FluidStorageKeys.LIQUID);
        FluidStorage.FluidEntry becEntry = property.getEntry(BecFluids.BEC);
        if (liquid == null || becEntry == null || becEntry.getBuilder() == null ||
                becEntry.getBuilder().still() == null) {
            return false;
        }

        ResourceLocation target = becEntry.getBuilder().still();

        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(liquid);
        ResourceLocation baseSprite = ext.getStillTexture();
        int tint = ext.getTintColor();

        try (AnimatedTexture base = AnimatedTexture.load(rm, baseSprite)) {
            Composite composite = composite(base, tint, overlay, BecConfig.maxAnimationFrames());
            try (NativeImage image = composite.image) {
                ResourceLocation png = new ResourceLocation(target.getNamespace(),
                        "textures/" + target.getPath() + ".png");
                GTDynamicResourcePack.addResource(png, image.asByteArray());
                if (composite.mcmeta != null) {
                    GTDynamicResourcePack.addResource(
                            new ResourceLocation(png.getNamespace(), png.getPath() + ".mcmeta"),
                            composite.mcmeta.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        layers.put(target, new BecSplitRender.Layers(baseSprite, tint));
        return true;
    }

    private record Composite(NativeImage image, JsonObject mcmeta) {}

    private static Composite composite(AnimatedTexture base, int tint, AnimatedTexture overlay, int maxFrames) {
        int step = 0;
        for (int t : base.seqTimes) step = gcd(step, t);
        for (int t : overlay.seqTimes) step = gcd(step, t);
        step = Math.max(1, step);

        long period = lcm(base.totalTicks, overlay.totalTicks);
        if (period / step > maxFrames) {
            period = overlay.totalTicks;
        }
        int steps = (int) Math.max(1, period / step);

        Long2IntLinkedOpenHashMap unique = new Long2IntLinkedOpenHashMap();
        List<int[]> timeline = new ArrayList<>();
        for (int s = 0; s < steps; s++) {
            int tick = s * step;
            long pair = ((long) base.frameAt(tick) << 32) | (overlay.frameAt(tick) & 0xFFFFFFFFL);
            int idx;
            if (unique.containsKey(pair)) {
                idx = unique.get(pair);
            } else {
                idx = unique.size();
                unique.put(pair, idx);
            }
            if (!timeline.isEmpty() && timeline.get(timeline.size() - 1)[0] == idx) {
                timeline.get(timeline.size() - 1)[1] += step;
            } else {
                timeline.add(new int[] { idx, step });
            }
        }

        int outW = Math.max(base.frameWidth, overlay.frameWidth);
        int outH = Math.max(base.frameHeight, overlay.frameHeight);
        NativeImage out = new NativeImage(NativeImage.Format.RGBA, outW, outH * unique.size(), false);

        int tA = (tint >>> 24) & 0xFF, tR = (tint >> 16) & 0xFF, tG = (tint >> 8) & 0xFF, tB = tint & 0xFF;
        for (var e : unique.long2IntEntrySet()) {
            int bFrame = (int) (e.getLongKey() >>> 32);
            int oFrame = (int) e.getLongKey();
            int yOff = e.getIntValue() * outH;
            for (int y = 0; y < outH; y++) {
                for (int x = 0; x < outW; x++) {
                    int b = base.sample(bFrame, x, y, outW, outH);
                    int o = overlay.sample(oFrame, x, y, outW, outH);
                    out.setPixelRGBA(x, yOff + y, blend(b, tA, tR, tG, tB, o));
                }
            }
        }

        JsonObject mcmeta = null;
        if (unique.size() > 1) {
            JsonArray frames = new JsonArray();
            for (int[] f : timeline) {
                JsonObject frame = new JsonObject();
                frame.addProperty("index", f[0]);
                frame.addProperty("time", f[1]);
                frames.add(frame);
            }
            JsonObject animation = new JsonObject();
            animation.addProperty("frametime", step);
            animation.add("frames", frames);
            mcmeta = new JsonObject();
            mcmeta.add("animation", animation);
        }
        return new Composite(out, mcmeta);
    }

    private static int blend(int base, int tA, int tR, int tG, int tB, int over) {
        float bA = ((base >>> 24) & 0xFF) * tA / (255f * 255f);
        float bR = (base & 0xFF) * tR / 255f;
        float bG = ((base >> 8) & 0xFF) * tG / 255f;
        float bB = ((base >> 16) & 0xFF) * tB / 255f;

        float oA = ((over >>> 24) & 0xFF) / 255f;
        float oR = over & 0xFF, oG = (over >> 8) & 0xFF, oB = (over >> 16) & 0xFF;

        float a = oA + bA * (1 - oA);
        if (a <= 0) return 0;
        int r = clamp((oR * oA + bR * bA * (1 - oA)) / a);
        int g = clamp((oG * oA + bG * bA * (1 - oA)) / a);
        int b = clamp((oB * oA + bB * bA * (1 - oA)) / a);
        return (clamp(a * 255f) << 24) | (b << 16) | (g << 8) | r;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return Math.abs(a);
    }

    private static long lcm(long a, long b) {
        if (a == 0 || b == 0) return Math.max(a, b);
        return a / gcd((int) a, (int) b) * b;
    }
}
