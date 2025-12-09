package me.voidxwalker.autoreset.mixin;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Local;
import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import me.voidxwalker.autoreset.mixin.access.ScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.LoadingScreenRenderer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.level.LevelInfo;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// We set priority to 500 so our executeReset inject runs right before worldpreview checks if it should reset
@Mixin(value = MinecraftClient.class, priority = 500)
public abstract class MinecraftClientMixin {
    @Shadow
    @Nullable
    public Screen currentScreen;
    @Shadow
    @Nullable
    public ClientWorld world;

    @Shadow
    public abstract void setScreen(Screen screen);
    @Shadow
    public abstract void connect(ClientWorld world);

    @Inject(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MinecraftClient;runGameLoop()V",
                    shift = At.Shift.AFTER
            )
    )
    private void executeReset(CallbackInfo ci) {
        while (Atum.shouldReset()) {
            if (Atum.isInWorld()) {
                Screen gameMenuScreen = new GameMenuScreen();
                gameMenuScreen.init(MinecraftClient.getInstance(), 0, 0);
                if (!this.clickButton(gameMenuScreen, "fast_reset.menu.quitWorld", "menu.quitWorld", "menu.returnToMenu", "menu.disconnect", "Quit to Title") || Atum.isInWorld()) {
                    if (this.world != null) {
                        this.world.disconnect();
                        this.connect(null);
                    }
                    this.setScreen(new TitleScreen());
                }
            }
            Atum.createNewWorld();
        }
    }

    @Inject(
            method = "startIntegratedServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/integrated/IntegratedServer;isLoading()Z",
                    shift = At.Shift.AFTER
            )
    )
    private void resetPreview(CallbackInfo ci) {
        if (Atum.HAS_WORLDPREVIEW && Atum.isResetScheduled()) {
            this.clickButton(this.currentScreen, "menu.returnToMenu");
        }
    }

    @ModifyReceiver(
            method = "startIntegratedServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/LoadingScreenRenderer;setTitle(Ljava/lang/String;)V"
            )
    )
    private LoadingScreenRenderer addSeedToLSR(LoadingScreenRenderer renderer, String title, @Local(argsOnly = true) LevelInfo levelInfo) {
        String seed = ((ISeedStringHolder) (Object) levelInfo).atum$getSeedString();
        if (seed != null) {
            ((ISeedStringHolder) renderer).atum$setSeedString(seed);
        }
        return renderer;
    }

    @ModifyArg(
            method = "startIntegratedServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"
            )
    )
    private Screen doNotOpenTitleScreen(Screen screen) {
        if (Atum.isRunning()) {
            return new Screen() {
                @Override
                public void render(int mouseX, int mouseY, float tickDelta) {
                    this.renderBackground();
                }
            };
        }
        return screen;
    }

    @Inject(
            method = "cleanUpAfterCrash",
            at = @At("HEAD")
    )
    private void stopRunningOnCrash(CallbackInfo ci) {
        Atum.stopRunning();
    }

    @Inject(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MinecraftClient;runGameLoop()V"
            )
    )
    private void checkSeedFailures(CallbackInfo ci) {
        Atum.checkSeedFailures();
    }

    @Unique
    private boolean clickButton(Screen screen, String... translationKeys) {
        for (String translationKey : translationKeys) {
            String translation = I18n.translate(translationKey);
            for (ButtonWidget button : ((ScreenAccessor) screen).atum$getButtons()) {
                if (translation.equals(button.message)) {
                    ((ScreenAccessor) screen).atum$buttonClicked(button);
                    return true;
                }
            }
        }
        return false;
    }
}
