package me.voidxwalker.autoreset;

import me.contaria.speedrunapi.util.TextUtil;
import me.voidxwalker.autoreset.mixin.access.CreateWorldScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.VanillaDataPackProvider;
import net.minecraft.server.SaveLoading;
import net.minecraft.util.Util;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPreset;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.WorldGenSettings;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

public class AtumCreateWorldScreen extends CreateWorldScreen {
    private final Job job;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private AtumCreateWorldScreen(MinecraftClient client, @Nullable Screen parent, GeneratorOptionsHolder generatorOptionsHolder, Optional<RegistryKey<WorldPreset>> defaultWorldType, OptionalLong seed, Job job) {
        super(client, parent, generatorOptionsHolder, defaultWorldType, seed);
        this.job = job;
    }

    public static AtumCreateWorldScreen create(@Nullable Screen parent) {
        return create(parent, Job.CREATION);
    }

    public static AtumCreateWorldScreen create(@Nullable Screen parent, Job job) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreenAndRender(new MessageScreen(TextUtil.translatable("createWorld.preparing")));
        ResourcePackManager resourcePackManager = new ResourcePackManager(new VanillaDataPackProvider());
        SaveLoading.ServerConfig serverConfig = CreateWorldScreenAccessor.atum$createServerConfig(resourcePackManager, DataConfiguration.SAFE_MODE);
        CompletableFuture<GeneratorOptionsHolder> completableFuture = SaveLoading.load(serverConfig, (context) -> new SaveLoading.LoadContext<>(new WorldCreationSettings(new WorldGenSettings(GeneratorOptions.createRandom(), WorldPresets.createDemoOptions(context.worldGenRegistryManager())), context.dataConfiguration()), context.dimensionsRegistryManager()), (resourceManager, dataPackContents, combinedDynamicRegistries, generatorOptions) -> {
            resourceManager.close();
            return new GeneratorOptionsHolder(generatorOptions.worldGenSettings(), combinedDynamicRegistries, dataPackContents, generatorOptions.dataConfiguration());
        }, Util.getMainWorkerExecutor(), client);
        client.runTasks(completableFuture::isDone);
        return new AtumCreateWorldScreen(client, parent, completableFuture.join(), Optional.of(WorldPresets.DEFAULT), OptionalLong.empty(), job);
    }

    public Job getJob() {
        return job;
    }

    public enum Job {
        CREATION,
        CONFIGURATION
    }
}
