package me.voidxwalker.autoreset.mixin.access;

import net.minecraft.client.gui.screen.world.LevelScreenProvider;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelScreenProvider.class)
public interface LevelScreenProviderAccessor {
    @Invoker("createModifier")
    static GeneratorOptionsHolder.RegistryAwareModifier atum$createFlatModifier(FlatChunkGeneratorConfig config) {
        throw new UnsupportedOperationException();
    }
}
