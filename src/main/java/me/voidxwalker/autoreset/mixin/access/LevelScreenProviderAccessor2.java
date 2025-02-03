package me.voidxwalker.autoreset.mixin.access;

import net.minecraft.client.gui.screen.world.LevelScreenProvider;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelScreenProvider.class)
public interface LevelScreenProviderAccessor2 {
    @Invoker("createModifier")
    static GeneratorOptionsHolder.RegistryAwareModifier atum$createSingleBiomeModifier(RegistryEntry<Biome> biomeEntry) {
        throw new UnsupportedOperationException();
    }
}
