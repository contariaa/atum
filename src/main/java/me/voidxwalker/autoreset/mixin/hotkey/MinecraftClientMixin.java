package me.voidxwalker.autoreset.mixin.hotkey;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/input/Keyboard;getEventKey()I",
                    ordinal = 0
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
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/input/Mouse;getEventButton()I"
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
