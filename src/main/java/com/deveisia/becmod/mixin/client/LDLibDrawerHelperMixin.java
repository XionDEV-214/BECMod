package com.deveisia.becmod.mixin.client;

import com.deveisia.becmod.client.BecSplitRender;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DrawerHelper.class, remap = false)
public abstract class LDLibDrawerHelperMixin {
    @Inject(method = "drawFluidTexture(Lnet/minecraft/client/gui/GuiGraphics;FFLnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFI)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void becmod$splitCondensate(GuiGraphics graphics, float x, float y, TextureAtlasSprite sprite,
                                               float maskTop, float maskRight, float zLevel, int color,
                                               CallbackInfo ci) {
        BecSplitRender.Layers layers = BecSplitRender.get(sprite);
        if (layers == null) return;

        DrawerHelper.drawFluidTexture(graphics, x, y, BecSplitRender.sprite(layers.baseSprite()),
                maskTop, maskRight, zLevel, BecSplitRender.multiply(color, layers.baseTint()));
        DrawerHelper.drawFluidTexture(graphics, x, y, BecSplitRender.overlaySprite(),
                maskTop, maskRight, zLevel, color);
        ci.cancel();
    }
}
