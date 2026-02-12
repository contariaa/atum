package me.voidxwalker.autoreset.mixin.gui;

import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LoadingScreenRenderer;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(LoadingScreenRenderer.class)
public abstract class LoadingScreenRendererMixin implements ISeedStringHolder {
    @Shadow
    private MinecraftClient client;
    @Shadow
    private Window window;

    @Unique
    private String seedString;

    @Inject(
            method = "setProgressPercentage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/font/TextRenderer;drawWithShadow(Ljava/lang/String;FFI)I",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            )
    )
    private void renderSeed(CallbackInfo ci) {
        if (!Atum.isRunning()) {
            return;
        }
        if (this.seedString != null && !this.seedString.isEmpty()) {
            this.client.textRenderer.drawWithShadow(
                    this.seedString,
                    (this.window.getWidth() - this.client.textRenderer.getStringWidth(this.seedString)) / 2.0f,
                    this.window.getHeight() / 2.0f - 4 - 40,
                    0xFFFFFF
            );
        }
    }

    @Override
    public void atum$setSeedString(String seedString) {
        this.seedString = Objects.requireNonNull(seedString);
    }

    @Override
    public String atum$getSeedString() {
        return this.seedString;
    }

    @Override
    public void atum$clearSeedString() {
        this.seedString = null;
    }
}
