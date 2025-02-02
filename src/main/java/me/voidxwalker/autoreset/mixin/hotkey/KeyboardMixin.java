package me.voidxwalker.autoreset.mixin.hotkey;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    // injecting at Keyboard#debugCrashStartTime ensures the window handle check has succeeded
    @Inject(
            method = "onKey",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/Keyboard;debugCrashStartTime:J",
                    ordinal = 0
            ),
            cancellable = true
    )
    private void onKey(long window, int key, int scancode, int action, int j, CallbackInfo ci) {
        // 1 is GLFW for "clicked" (0 -> "released", 2 -> "held down")
        if (action == 1 && Atum.resetKey.matchesKey(key, scancode)) {
            if (this.client.currentScreen instanceof KeybindsScreen && ((KeybindsScreen) this.client.currentScreen).selectedKeyBinding == Atum.resetKey) {
                return;
            }
            Atum.scheduleReset();
            ci.cancel();
        }
    }
}
