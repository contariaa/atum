package me.voidxwalker.autoreset.mixin.hotkey;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.screen.options.ControlsListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ControlsListWidget.class)
public abstract class ControlsListWidgetMixin {

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Arrays;sort([Ljava/lang/Object;)V",
                    remap = false
            )
    )
    private void moveAtumResetKeyToEnd(Object[] keyBindings, Operation<Void> original) {
        original.call((Object) keyBindings);
        for (int i = 0; i < keyBindings.length; i++) {
            if (keyBindings[i] != Atum.resetKey) {
                continue;
            }
            for (; i < keyBindings.length - 1; i++) {
                keyBindings[i] = keyBindings[i + 1];
            }
            keyBindings[i] = Atum.resetKey;
        }
    }
}
