package me.voidxwalker.autoreset.mixin.gui;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SettingsScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SettingsScreen.class)
public abstract class SettingsScreenMixin extends Screen {

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void addStopResetsButton(CallbackInfo ci) {
        if (Atum.isRunning()) {
            this.buttons.add(new ButtonWidget(9383742, 0, this.height - 20, 100, 20, I18n.translate("atum.menu.stop_resets")));
        }
    }

    @Inject(
            method = "buttonClicked",
            at = @At("TAIL")
    )
    private void stopResetting(ButtonWidget button, CallbackInfo ci) {
        if (button.id == 9383742) {
            button.active = false;
            Atum.stopRunning();
            if (this.client != null && this.client.world != null) {
                this.client.world.disconnect();
                this.client.connect(null);
                this.client.setScreen(new TitleScreen());
            }
        }
    }
}
