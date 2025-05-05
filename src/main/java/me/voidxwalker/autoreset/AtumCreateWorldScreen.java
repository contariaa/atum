package me.voidxwalker.autoreset;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.MoreOptionsDialog;
import net.minecraft.client.world.GeneratorType;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.gen.GeneratorOptions;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalLong;

public class AtumCreateWorldScreen extends CreateWorldScreen {
    private final Job job;

    private AtumCreateWorldScreen(@Nullable Screen parent, DataPackSettings dataPackSettings, MoreOptionsDialog moreOptionsDialog, Job job) {
        super(parent, dataPackSettings, moreOptionsDialog);
        this.job = job;
    }

    public static AtumCreateWorldScreen create(@Nullable Screen parent) {
        return create(parent, Job.CREATION);
    }

    public static AtumCreateWorldScreen create(@Nullable Screen parent, Job job) {
        DynamicRegistryManager.Immutable registryManager = DynamicRegistryManager.BUILTIN.get();
        return new AtumCreateWorldScreen(parent, DataPackSettings.SAFE_MODE, new MoreOptionsDialog(registryManager, GeneratorOptions.getDefaultOptions(registryManager), Optional.of(GeneratorType.DEFAULT), OptionalLong.empty()), job);
    }

    public Job getJob() {
        return job;
    }

    public enum Job {
        CREATION,
        CONFIGURATION
    }
}
