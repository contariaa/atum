package me.voidxwalker.autoreset.mixin.gui;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

    @ModifyReturnValue(
            method = "getRightText",
            at = @At("RETURN")
    )
    private List<String> modifyRightText(List<String> debugText) {
        if (Atum.isRunning()) {
            debugText.addAll(Atum.config.getDebugText());
        }
        return debugText;
    }
}
