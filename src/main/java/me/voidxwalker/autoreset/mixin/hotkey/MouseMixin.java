package me.voidxwalker.autoreset.mixin.hotkey;

import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.Screen;
import net.minecraft.client.gui.menu.options.ControlsOptionsScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    // injecting at MinecraftClient#IS_SYSTEM_MAC ensures the window handle check has succeeded
    @Inject(
            method = "onMouseButton",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/MinecraftClient;IS_SYSTEM_MAC:Z",
                    ordinal = 0
            ),
            cancellable = true
    )
    private void onKey(long window, int button, int action, int mods, CallbackInfo ci) {
        // 1 is GLFW for "clicked" (0 -> "released", 2 -> "held down")
        if (action == 1 && Atum.resetKey.matchesMouse(button)) {
            Screen screen = this.client.currentScreen;
            if (screen instanceof ControlsOptionsScreen && ((ControlsOptionsScreen) screen).focusedBinding == Atum.resetKey) {
                return;
            }
            Atum.scheduleReset();
            ci.cancel();
        }
    }
}
