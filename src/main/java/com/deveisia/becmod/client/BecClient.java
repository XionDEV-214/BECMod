package com.deveisia.becmod.client;

import com.gregtechceu.gtceu.data.pack.event.RegisterDynamicResourcesEvent;

import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

public final class BecClient {
    private BecClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(EventPriority.NORMAL, false, RegisterDynamicResourcesEvent.class,
                BecTextureGenerator::onRegisterDynamicResources);
    }
}
