package me.voidxwalker.autoreset.mixin.access;

import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.registry.CombinedDynamicRegistries;
import net.minecraft.registry.ServerDynamicRegistryType;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.SaveLoading;
import net.minecraft.world.SaveProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
    @Invoker("createServerConfig")
    static SaveLoading.ServerConfig atum$createServerConfig(ResourcePackManager dataPackManager, DataConfiguration dataConfiguration) {
        throw new UnsupportedOperationException();
    }

    @Invoker("startServer")
    boolean atum$startServer(CombinedDynamicRegistries<ServerDynamicRegistryType> combinedDynamicRegistries, SaveProperties saveProperties);
}
