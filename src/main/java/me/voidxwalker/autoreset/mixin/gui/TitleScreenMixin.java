package me.voidxwalker.autoreset.mixin.gui;

import me.contaria.speedrunapi.util.IdentifierUtil;
import me.contaria.speedrunapi.util.TextUtil;
import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.AtumCreateWorldScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Unique
    private static final Identifier BUTTON_IMAGE = IdentifierUtil.ofVanilla("textures/item/golden_boots.png");

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void init(CallbackInfo info) {
        if (Atum.isRunning()) {
            Atum.scheduleReset();
            return;
        }

        this.addDrawableChild(new ButtonWidget(this.width / 2 - 124, this.height / 4 + 48, 20, 20, TextUtil.empty(), button -> {
            if (Screen.hasShiftDown()) {
                MinecraftClient.getInstance().setScreen(AtumCreateWorldScreen.create(this));
                return;
            }
            Atum.scheduleReset();
        }, Supplier::get) {
            @Override
            public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
                super.renderButton(context, mouseX, mouseY, delta);

                context.drawTexture(BUTTON_IMAGE, this.getX() + 2, this.getY() + 2, 0.0F, 0.0F, 16, 16, 16, 16);
                if (Screen.hasShiftDown() && this.isHovered()) {
                    context.drawCenteredTextWithShadow(TitleScreenMixin.this.textRenderer, TextUtil.translatable("atum.menu.open_config"), this.getX() + this.getWidth() / 2, this.getY() - 15, 16777215);
                }
            }
        });
    }
}
