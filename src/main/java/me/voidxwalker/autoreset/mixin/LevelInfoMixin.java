package me.voidxwalker.autoreset.mixin;

import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import net.minecraft.world.level.LevelInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Objects;

@Mixin(LevelInfo.class)
public abstract class LevelInfoMixin implements ISeedStringHolder {
    @Unique
    private String seedString;

    @Override
    public void atum$setSeedString(String seedString) {
        Atum.ensureState(this.seedString == null, "Seed string for this GeneratorOptions has already been set!");
        this.seedString = Objects.requireNonNull(seedString);
    }

    @Override
    public String atum$getSeedString() {
        return this.seedString;
    }
}
