package me.voidxwalker.autoreset.mixin.gui;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.screen.SaveLevelScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.options.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
    protected OptionsScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addStopResetsButton(CallbackInfo ci) {
        if (Atum.isRunning()) {
            Text stopResetsText = new TranslatableText("atum.menu.stop_resets");
            int width = this.textRenderer.getWidth(stopResetsText) + 20;
            this.addButton(new ButtonWidget(0, this.height - 20, width, 20, stopResetsText, button -> {
                button.active = false;
                Atum.stopRunning();
                if (this.client != null && this.client.world != null) {
                    this.client.world.disconnect();
                    this.client.disconnect(new SaveLevelScreen(new TranslatableText("menu.savingLevel")));
                    this.client.openScreen(new TitleScreen());
                }
            }));
        }
    }
}
