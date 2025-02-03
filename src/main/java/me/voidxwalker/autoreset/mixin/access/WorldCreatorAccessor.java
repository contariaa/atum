package me.voidxwalker.autoreset.mixin.access;

import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldCreator.class)
public interface WorldCreatorAccessor {
    @Accessor("cheatsEnabled")
    Boolean atum$getCheatsEnabled();

    @Accessor("gameMode")
    WorldCreator.Mode atum$getGameMode();

    @Accessor("difficulty")
    Difficulty atum$getDifficulty();

    @Accessor("generateStructures")
    boolean atum$shouldGenerateStructures();

    @Accessor("bonusChestEnabled")
    boolean atum$isBonusChestEnabled();
}
