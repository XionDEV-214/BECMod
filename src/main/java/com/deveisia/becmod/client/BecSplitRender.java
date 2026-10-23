package com.deveisia.becmod.client;

import com.deveisia.becmod.BecMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class BecSplitRender {
    public static final ResourceLocation OVERLAY = BecMod.id("block/fluids/bec_condensate_overlay");

    public record Layers(ResourceLocation baseSprite, int baseTint) {}

    private static volatile Map<ResourceLocation, Layers> layers = Map.of();

    private BecSplitRender() {}

    static void setLayers(Map<ResourceLocation, Layers> newLayers) {
        layers = Map.copyOf(newLayers);
    }

    public static @Nullable Layers get(@Nullable TextureAtlasSprite sprite) {
        if (sprite == null) return null;
        Map<ResourceLocation, Layers> map = layers;
        if (map.isEmpty()) return null;
        return map.get(sprite.contents().name());
    }

    public static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    public static TextureAtlasSprite overlaySprite() {
        return sprite(OVERLAY);
    }

    public static int multiply(int a, int b) {
        int al = ((a >>> 24) * (b >>> 24)) / 255;
        int r = (((a >> 16) & 0xFF) * ((b >> 16) & 0xFF)) / 255;
        int g = (((a >> 8) & 0xFF) * ((b >> 8) & 0xFF)) / 255;
        int bl = ((a & 0xFF) * (b & 0xFF)) / 255;
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }
}
