package me.voidxwalker.autoreset.mixin.hotkey;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(
            method = "handleInput",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/Screen;handleKeyboard()V"
            )
    )
    private void resetOnKey(CallbackInfo ci) {
        if (!Atum.canReset()) {
            return;
        }
        int code = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
        if (code == Atum.resetKey.getCode() && Keyboard.getEventKeyState() && !Keyboard.isRepeatEvent()) {
            Atum.scheduleReset();
        }
    }

    @Inject(
            method = "handleInput",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/Screen;handleMouse()V"
            )
    )
    private void resetOnMouseKey(CallbackInfo ci) {
        if (!Atum.canReset()) {
            return;
        }
        int code = Mouse.getEventButton() - 100;
        if (code == Atum.resetKey.getCode() && Mouse.getEventButtonState()) {
            Atum.scheduleReset();
        }
    }
}
