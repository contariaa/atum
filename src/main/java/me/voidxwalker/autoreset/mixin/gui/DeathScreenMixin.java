package me.voidxwalker.autoreset.mixin.gui;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.screen.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin {

    @Inject(
            method = "quitLevel",
            at = @At("HEAD")
    )
    private void stopResettingOnDeathQuit(CallbackInfo ci) {
        Atum.stopRunning();
    }
}
