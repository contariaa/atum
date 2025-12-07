package me.voidxwalker.autoreset.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.WorldGenerationProgressScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Objects;

@Mixin(WorldGenerationProgressScreen.class)
public abstract class WorldGenerationProgressScreenMixin implements ISeedStringHolder {
    @Unique
    private String seedString;

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/WorldGenerationProgressScreen;drawCenteredString(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"))
    private void drawSeedString(WorldGenerationProgressScreen screen, TextRenderer textRenderer, String s, int x, int y, int color, Operation<Void> original) {
        original.call(screen, textRenderer, s, x, y, color);
        if (!Atum.isRunning()) {
            return;
        }
        if (Atum.inDemoMode()) {
            screen.drawCenteredString(textRenderer, "North Carolina", x, y - 20, color);
        } else if (!this.seedString.isEmpty()) {
            screen.drawCenteredString(textRenderer, Atum.getSeedProvider().shouldShowSeed() ? this.seedString : "Set Seed", x, y - 20, color);
        }
    }

    @Override
    public void atum$setSeedString(String seedString) {
        Atum.ensureState(this.seedString == null, "Seed string for this LevelLoadingScreen has already been set!");
        this.seedString = Objects.requireNonNull(seedString);
    }

    @Override
    public String atum$getSeedString() {
        return this.seedString;
    }
}
