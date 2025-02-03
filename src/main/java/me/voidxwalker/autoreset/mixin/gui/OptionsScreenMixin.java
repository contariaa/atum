package me.voidxwalker.autoreset.mixin.gui;

import me.contaria.speedrunapi.util.TextUtil;
import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {

    protected OptionsScreenMixin(Text title) {
        super(title);
    }

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void addStopResetsButton(CallbackInfo ci) {
        if (Atum.isRunning()) {
            this.addDrawableChild(ButtonWidget.builder(TextUtil.translatable("atum.menu.stop_resets"), button -> {
                button.active = false;
                Atum.stopRunning();
                if (this.client != null && this.client.world != null) {
                    this.client.world.disconnect();
                    this.client.disconnect(new MessageScreen(TextUtil.translatable("menu.savingLevel")));
                    this.client.setScreen(new TitleScreen());
                }
            }).dimensions(0, this.height - 20, 100, 20).build());
        }
    }
}
