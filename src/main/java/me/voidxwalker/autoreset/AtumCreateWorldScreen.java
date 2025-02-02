package me.voidxwalker.autoreset;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import org.jetbrains.annotations.Nullable;

public class AtumCreateWorldScreen extends CreateWorldScreen {

    private AtumCreateWorldScreen(@Nullable Screen parent, LevelInfo levelInfo, DynamicRegistryManager.Impl registryManager) {
        super(parent, levelInfo, GeneratorOptions.getDefaultOptions(registryManager.get(Registry.DIMENSION_TYPE_KEY), registryManager.get(Registry.BIOME_KEY), registryManager.get(Registry.CHUNK_GENERATOR_SETTINGS_KEY)), null, DataPackSettings.SAFE_MODE, registryManager);
    }

    public static AtumCreateWorldScreen create(@Nullable Screen parent) {
        return new AtumCreateWorldScreen(
                parent,
                new LevelInfo("", GameMode.SURVIVAL, false, Difficulty.EASY, false, new GameRules(), DataPackSettings.SAFE_MODE),
                DynamicRegistryManager.create()
        );
    }
}
