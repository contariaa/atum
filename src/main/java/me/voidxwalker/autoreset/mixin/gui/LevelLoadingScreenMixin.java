package me.voidxwalker.autoreset.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Objects;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin implements ISeedStringHolder {
    @Unique
    private String seedString;

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"
            )
    )
    private void drawSeedString(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, int color, Operation<Void> original) {
        original.call(context, textRenderer, text, x, y, color);
        if (!Atum.isRunning()) {
            return;
        }
        if (Atum.inDemoMode()) {
            context.drawCenteredTextWithShadow(textRenderer, "North Carolina", x, y - 20, color);
        } else if (this.seedString != null && !this.seedString.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Atum.getSeedProvider().shouldShowSeed() ? this.seedString : "Set Seed", x, y - 20, color);
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
