package me.voidxwalker.autoreset.mixin.gui;

import me.contaria.speedrunapi.util.TextUtil;
import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.CloseWorldScreen;
import net.minecraft.client.gui.MainMenuScreen;
import net.minecraft.client.gui.Screen;
import net.minecraft.client.gui.menu.SettingsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SettingsScreen.class)
public abstract class SettingsScreenMixin extends Screen {

    protected SettingsScreenMixin(Component title) {
        super(title);
    }

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void addStopResetsButton(CallbackInfo ci) {
        if (Atum.isRunning()) {
            this.addButton(new ButtonWidget(0, this.height - 20, 100, 20, I18n.translate("atum.menu.stop_resets"), button -> {
                button.active = false;
                Atum.stopRunning();
                if (this.minecraft != null && this.minecraft.world != null) {
                    this.minecraft.world.disconnect();
                    this.minecraft.disconnect(new CloseWorldScreen(TextUtil.translatable("menu.savingLevel")));
                    this.minecraft.openScreen(new MainMenuScreen());
                }
            }));
        }
    }
}
