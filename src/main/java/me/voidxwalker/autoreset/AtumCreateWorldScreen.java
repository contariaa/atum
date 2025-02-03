package me.voidxwalker.autoreset;

import me.contaria.speedrunapi.util.TextUtil;
import me.voidxwalker.autoreset.mixin.access.CreateWorldScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldCallback;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreationSettings;
import net.minecraft.client.world.GeneratorOptionsFactory;
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

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class AtumCreateWorldScreen extends CreateWorldScreen {

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private AtumCreateWorldScreen(MinecraftClient client, @Nullable Screen parent, GeneratorOptionsHolder generatorOptionsHolder, Optional<RegistryKey<WorldPreset>> defaultWorldType, OptionalLong seed, CreateWorldCallback callback) {
        super(client, parent, generatorOptionsHolder, defaultWorldType, seed, callback);
    }

    public static AtumCreateWorldScreen create(@Nullable Screen parent) {
        MinecraftClient client = MinecraftClient.getInstance();

        Function<SaveLoading.LoadContextSupplierContext, WorldGenSettings> settingsSupplier = context -> new WorldGenSettings(GeneratorOptions.createRandom(), WorldPresets.createDemoOptions(context.worldGenRegistryManager()));
        GeneratorOptionsFactory generatorOptionsFactory = (dataPackContents, dynamicRegistries, settings) -> new GeneratorOptionsHolder(settings.worldGenSettings(), dynamicRegistries, dataPackContents, settings.dataConfiguration());
        CreateWorldCallback callback = (screen, combinedDynamicRegistries, levelProperties, dataPackTempDir) -> ((CreateWorldScreenAccessor) screen).atum$startServer(combinedDynamicRegistries, levelProperties);

        client.setScreenAndRender(new MessageScreen(TextUtil.translatable("createWorld.preparing")));
        ResourcePackManager resourcePackManager = new ResourcePackManager(new VanillaDataPackProvider(client.getSymlinkFinder()));
        SaveLoading.ServerConfig serverConfig = CreateWorldScreenAccessor.atum$createServerConfig(resourcePackManager, DataConfiguration.SAFE_MODE);
        CompletableFuture<GeneratorOptionsHolder> completableFuture = SaveLoading.load(serverConfig, (context) -> new SaveLoading.LoadContext<>(new WorldCreationSettings(settingsSupplier.apply(context), context.dataConfiguration()), context.dimensionsRegistryManager()), (resourceManager, dataPackContents, dynamicRegistries, settings) -> {
            resourceManager.close();
            return generatorOptionsFactory.apply(dataPackContents, dynamicRegistries, settings);
        }, Util.getMainWorkerExecutor(), client);
        Objects.requireNonNull(completableFuture);
        client.runTasks(completableFuture::isDone);
        return new AtumCreateWorldScreen(client, parent, completableFuture.join(), Optional.of(WorldPresets.DEFAULT), OptionalLong.empty(), callback);
    }
}
