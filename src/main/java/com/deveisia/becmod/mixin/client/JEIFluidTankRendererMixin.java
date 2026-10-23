package com.deveisia.becmod.mixin.client;

import com.deveisia.becmod.BecMod;
import com.deveisia.becmod.client.BecSplitRender;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "mezz.jei.library.render.FluidTankRenderer", remap = false)
public abstract class JEIFluidTankRendererMixin {
    @Unique
    private static MethodHandle becmod$drawTiled;
    @Unique
    private static boolean becmod$failed;

    @Inject(method = "drawTiledSprite(Lnet/minecraft/client/gui/GuiGraphics;IIIJLnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lmezz/jei/api/gui/drawable/TilingDirection;II)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void becmod$splitCondensate(GuiGraphics graphics, int tiledWidth, int tiledHeight, int color,
                                               long scaledAmount, TextureAtlasSprite sprite,
                                               @Coerce Object tilingDirection, int posX, int posY,
                                               CallbackInfo ci) {
        BecSplitRender.Layers layers = BecSplitRender.get(sprite);
        if (layers == null || becmod$failed) return;
        MethodHandle draw = becmod$handle(9);
        if (draw == null) return;
        try {
            draw.invoke(graphics, tiledWidth, tiledHeight, BecSplitRender.multiply(color, layers.baseTint()),
                    scaledAmount, BecSplitRender.sprite(layers.baseSprite()), tilingDirection, posX, posY);
            draw.invoke(graphics, tiledWidth, tiledHeight, color,
                    scaledAmount, BecSplitRender.overlaySprite(), tilingDirection, posX, posY);
        } catch (Throwable t) {
            becmod$fail(t);
            return;
        }
        ci.cancel();
    }

    @Inject(method = "drawTiledSprite(Lnet/minecraft/client/gui/GuiGraphics;IIIJLnet/minecraft/client/renderer/texture/TextureAtlasSprite;II)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void becmod$splitCondensateLegacy(GuiGraphics graphics, int tiledWidth, int tiledHeight,
                                                     int color, long scaledAmount, TextureAtlasSprite sprite,
                                                     int posX, int posY, CallbackInfo ci) {
        BecSplitRender.Layers layers = BecSplitRender.get(sprite);
        if (layers == null || becmod$failed) return;
        MethodHandle draw = becmod$handle(8);
        if (draw == null) return;
        try {
            draw.invoke(graphics, tiledWidth, tiledHeight, BecSplitRender.multiply(color, layers.baseTint()),
                    scaledAmount, BecSplitRender.sprite(layers.baseSprite()), posX, posY);
            draw.invoke(graphics, tiledWidth, tiledHeight, color,
                    scaledAmount, BecSplitRender.overlaySprite(), posX, posY);
        } catch (Throwable t) {
            becmod$fail(t);
            return;
        }
        ci.cancel();
    }

    @Unique
    private static MethodHandle becmod$handle(int paramCount) {
        if (becmod$drawTiled == null) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                for (Method m : lookup.lookupClass().getDeclaredMethods()) {
                    if (m.getName().equals("drawTiledSprite") && m.getParameterCount() == paramCount) {
                        becmod$drawTiled = lookup.unreflect(m);
                        break;
                    }
                }
                if (becmod$drawTiled == null) throw new NoSuchMethodException("drawTiledSprite/" + paramCount);
            } catch (ReflectiveOperationException e) {
                becmod$fail(e);
                return null;
            }
        }
        return becmod$drawTiled;
    }

    @Unique
    private static void becmod$fail(Throwable t) {
        becmod$failed = true;
        BecMod.LOGGER.error("JEI split condensate rendering disabled, falling back to composite textures", t);
    }
}
