package com.deveisia.becmod;

import com.deveisia.becmod.client.BecClient;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BecMod.MOD_ID)
public class BecMod {
    public static final String MOD_ID = "becmod";
    public static final Logger LOGGER = LoggerFactory.getLogger("BecMod");

    public BecMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        BecConfig.load();

        BecFluids.init();
        modBus.addListener(BecFluids::onPostMaterial);

        if (FMLEnvironment.dist.isClient()) {
            BecClient.init(modBus);
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
