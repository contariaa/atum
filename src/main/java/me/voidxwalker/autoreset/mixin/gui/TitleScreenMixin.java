package me.voidxwalker.autoreset.mixin.gui;

import me.contaria.speedrunapi.util.IdentifierUtil;
import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.AtumCreateWorldScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Unique
    private static final Identifier BUTTON_IMAGE = IdentifierUtil.ofVanilla("textures/items/gold_boots.png");

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void init(CallbackInfo info) {
        if (Atum.isRunning()) {
            Atum.scheduleReset();
            return;
        }

        this.buttons.add(new ButtonWidget(7538282, this.width / 2 - 124, this.height / 4 + 48, 20, 20, "") {
            @Override
            public void render(MinecraftClient client, int mouseX, int mouseY) {
                super.render(client, mouseX, mouseY);

                client.getTextureManager().bindTexture(BUTTON_IMAGE);
                DrawableHelper.drawTexture(this.x + 2, this.y + 2, 0.0F, 0.0F, 16, 16, 16, 16);
                if (Screen.hasShiftDown() && this.isHovered()) {
                    this.drawCenteredString(MinecraftClient.getInstance().textRenderer, I18n.translate("atum.menu.open_config"), this.x + this.width / 2, this.y - 15, 16777215);
                }
            }
        });
    }

    @Inject(
            method = "buttonClicked",
            at = @At("TAIL")
    )
    private void startResetting(ButtonWidget button, CallbackInfo ci) {
        if (button.id == 7538282) {
            if (Screen.hasShiftDown()) {
                this.client.setScreen(new AtumCreateWorldScreen(this, AtumCreateWorldScreen.Job.CONFIGURATION));
                return;
            }
            Atum.scheduleReset();
        }
    }
}
