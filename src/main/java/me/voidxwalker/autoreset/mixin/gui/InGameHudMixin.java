package me.voidxwalker.autoreset.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE:LAST",
                    target = "Lnet/minecraft/client/font/TextRenderer;method_956(Ljava/lang/String;III)I"
            )
    )
    private int modifyRightText(TextRenderer textRenderer, String string, int x, int y, int color, Operation<Integer> original) {
        int i = original.call(textRenderer, string, x, y, color);
        if (Atum.isRunning()) {
            y += 20;
            for (String line : Atum.config.getDebugText()) {
                textRenderer.method_956(line, x, y, color);
                y += 10;
            }
        }
        return i;
    }
}
