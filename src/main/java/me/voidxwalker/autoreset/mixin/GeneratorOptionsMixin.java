package me.voidxwalker.autoreset.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import net.minecraft.world.gen.GeneratorOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Objects;

@Mixin(GeneratorOptions.class)
public abstract class GeneratorOptionsMixin implements ISeedStringHolder {
    @Unique
    private String seedString;

    @ModifyReturnValue(
            method = {
                    "withBonusChest",
                    "withStructures",
                    "withSeed"
            },
            at = @At("RETURN")
    )
    private GeneratorOptions transferSeedString(GeneratorOptions options) {
        if (this.seedString != null) {
            ((ISeedStringHolder) options).atum$setSeedString(this.seedString);
        }
        return options;
    }

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
