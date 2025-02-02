package me.voidxwalker.autoreset.mixin.access;

import net.minecraft.client.world.GeneratorType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GeneratorOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GeneratorType.class)
public interface GeneratorTypeAccessor {
    @Accessor("DEFAULT")
    static GeneratorType atum$DEFAULT() {
        throw new UnsupportedOperationException();
    }

    @Accessor("FLAT")
    static GeneratorType atum$FLAT() {
        throw new UnsupportedOperationException();
    }

    @Accessor("LARGE_BIOMES")
    static GeneratorType atum$LARGE_BIOMES() {
        throw new UnsupportedOperationException();
    }

    @Accessor("AMPLIFIED")
    static GeneratorType atum$AMPLIFIED() {
        throw new UnsupportedOperationException();
    }

    @Accessor("SINGLE_BIOME_SURFACE")
    static GeneratorType atum$SINGLE_BIOME_SURFACE() {
        throw new UnsupportedOperationException();
    }

    @Accessor("SINGLE_BIOME_CAVES")
    static GeneratorType atum$SINGLE_BIOME_CAVES() {
        throw new UnsupportedOperationException();
    }

    @Accessor("SINGLE_BIOME_FLOATING_ISLANDS")
    static GeneratorType atum$SINGLE_BIOME_FLOATING_ISLANDS() {
        throw new UnsupportedOperationException();
    }

    @Accessor("DEBUG_ALL_BLOCK_STATES")
    static GeneratorType atum$DEBUG_ALL_BLOCK_STATES() {
        throw new UnsupportedOperationException();
    }

    @Invoker("createFixedBiomeOptions")
    static GeneratorOptions atum$createFixedBiomeOptions(GeneratorOptions generatorOptions, GeneratorType generatorType, Biome biome) {
        throw new UnsupportedOperationException();
    }
}
