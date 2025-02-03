package me.voidxwalker.autoreset.mixin.access;

import net.minecraft.world.level.LevelGeneratorType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelGeneratorType.class)
public interface GeneratorTypeAccessor {
    @Accessor("DEFAULT")
    static LevelGeneratorType atum$DEFAULT() {
        throw new UnsupportedOperationException();
    }

    @Accessor("FLAT")
    static LevelGeneratorType atum$FLAT() {
        throw new UnsupportedOperationException();
    }

    @Accessor("LARGE_BIOMES")
    static LevelGeneratorType atum$LARGE_BIOMES() {
        throw new UnsupportedOperationException();
    }

    @Accessor("AMPLIFIED")
    static LevelGeneratorType atum$AMPLIFIED() {
        throw new UnsupportedOperationException();
    }

    @Accessor("BUFFET")
    static LevelGeneratorType atum$BUFFET() {
        throw new UnsupportedOperationException();
    }

    @Accessor("DEBUG_ALL_BLOCK_STATES")
    static LevelGeneratorType atum$DEBUG_ALL_BLOCK_STATES() {
        throw new UnsupportedOperationException();
    }
}
