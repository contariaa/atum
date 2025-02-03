package me.voidxwalker.autoreset.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import me.contaria.speedrunapi.util.TextUtil;
import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.level.LevelInfo;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
    public abstract void openScreen(@Nullable Screen screen);

    @Shadow
    public abstract void disconnect(Screen screen);

    @Inject(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MinecraftClient;render(Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void executeReset(CallbackInfo ci) {
        while (Atum.shouldReset()) {
            if (Atum.isInWorld()) {
                Screen gameMenuScreen = new GameMenuScreen(true);
                gameMenuScreen.init(MinecraftClient.getInstance(), 0, 0);
                if (!this.clickButton(gameMenuScreen, "fast_reset.menu.quitWorld", "menu.quitWorld", "menu.returnToMenu", "menu.disconnect") || Atum.isInWorld()) {
                    if (this.world != null) {
                        this.world.disconnect();
                        this.disconnect(new SaveLevelScreen(TextUtil.translatable("menu.savingLevel")));
                    }
                    this.openScreen(new TitleScreen());
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

    @ModifyVariable(
            method = "startIntegratedServer",
            at = @At("STORE")
    )
    private LevelLoadingScreen addSeedToLLS(LevelLoadingScreen levelLoadingScreen, @Local(argsOnly = true) LevelInfo levelInfo) {
        String seed = ((ISeedStringHolder) (Object) levelInfo).atum$getSeedString();
        if (seed != null) {
            ((ISeedStringHolder) levelLoadingScreen).atum$setSeedString(seed);
        }
        return levelLoadingScreen;
    }

    @ModifyReturnValue(
            method = "isDemo",
            at = @At("RETURN")
    )
    private boolean demoMode(boolean isDemo) {
        return isDemo || Atum.inDemoMode();
    }

    @Inject(
            method = "cleanUpAfterCrash",
            at = @At("HEAD")
    )
    private void stopRunningOnCrash(CallbackInfo ci) {
        Atum.stopRunning();
    }

    @Unique
    private boolean clickButton(Screen screen, String... translationKeys) {
        for (String translationKey : translationKeys) {
            String translation = I18n.translate(translationKey);
            for (Element element : screen.children()) {
                if (!(element instanceof ButtonWidget)) {
                    continue;
                }
                ButtonWidget button = ((ButtonWidget) element);
                if (translation.equals(button.getMessage())) {
                    button.onPress();
                    return true;
                }
            }
        }
        return false;
    }
}
