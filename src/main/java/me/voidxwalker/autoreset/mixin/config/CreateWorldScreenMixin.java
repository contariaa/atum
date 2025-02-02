package me.voidxwalker.autoreset.mixin.config;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.voidxwalker.autoreset.AttemptTracker;
import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.AtumCreateWorldScreen;
import me.voidxwalker.autoreset.api.seedprovider.SeedProvider;
import me.voidxwalker.autoreset.interfaces.IMoreOptionsDialog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.MoreOptionsDialog;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {

    @Shadow
    @Final
    private Screen parent;

    @Shadow
    private Difficulty safeDifficulty;
    @Shadow
    private Difficulty difficulty;
    @Shadow
    private CreateWorldScreen.Mode currentMode;
    @Shadow
    private boolean cheatsEnabled;
    @Shadow
    private boolean tweakedCheats;
    @Shadow
    private GameRules gameRules;
    @Shadow
    protected DataPackSettings dataPackSettings;
    @Shadow
    private @Nullable Path dataPackTempDir;

    @Shadow
    @Final
    public MoreOptionsDialog moreOptionsDialog;
    @Shadow
    private boolean moreOptionsOpen;
    @Shadow
    private TextFieldWidget levelNameField;
    @Shadow
    private ButtonWidget createLevelButton;
    @Shadow
    private ButtonWidget dataPacksButton;

    @Unique
    private AbstractButtonWidget demoModeButton;

    @Shadow
    protected abstract void updateSaveFolderName();

    @Shadow
    protected abstract void createLevel();

    protected CreateWorldScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/gui/screen/world/MoreOptionsDialog;)V", at = @At("TAIL"))
    private void loadAtumConfigurations(CallbackInfo ci) {
        if (!this.isAtum()) {
            return;
        }

        this.currentMode = Atum.config.gameMode;
        this.safeDifficulty = this.difficulty = Atum.config.difficulty;
        this.cheatsEnabled = Atum.config.cheatsEnabled;
        this.tweakedCheats = true;
        if (Atum.config.hasModifiedGameRules()) {
            this.gameRules.setAllValues(Atum.config.gameRules, null);
        }
        this.dataPackSettings = new DataPackSettings(Atum.config.dataPackSettings.getEnabled(), Atum.config.dataPackSettings.getDisabled());

        ((IMoreOptionsDialog) this.moreOptionsDialog).atum$loadAtumConfigurations();

        if (!Atum.isRunning()) {
            this.dataPackTempDir = Atum.config.dataPackDirectory;
            return;
        }

        if (!Atum.config.isDefaultDataPackSettings(this.dataPackSettings)) {
            if (Files.isDirectory(Atum.config.dataPackDirectory)) {
                this.dataPackTempDir = CreateWorldScreen.copyDataPack(Atum.config.dataPackDirectory, this.client);
                if (this.dataPackTempDir == null) {
                    Atum.config.dataPackMismatch = true;
                    Atum.LOGGER.warn("Data pack mismatch, failed to copy data packs!");
                }
            } else {
                Atum.config.dataPackMismatch = true;
                Atum.LOGGER.warn("Data pack mismatch, the Atum data pack directory is missing!");
            }
        }
    }

    @WrapWithCondition(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;addButton(Lnet/minecraft/client/gui/widget/AbstractButtonWidget;)Lnet/minecraft/client/gui/widget/AbstractButtonWidget;", ordinal = 0), slice = @Slice(
            from = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screen/ScreenTexts;CANCEL:Lnet/minecraft/text/Text;")
    ))
    private boolean captureCancelButton(CreateWorldScreen screen, AbstractButtonWidget button) {
        return !this.isAtum();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void modifyAtumCreateWorldScreen(CallbackInfo info) {
        if (!this.isAtum()) {
            return;
        }

        String seed;

        if (Atum.isRunning()) {
            SeedProvider seedProvider = Atum.getSeedProvider();
            Optional<String> seedOpt = seedProvider.getSeed();
            if (seedOpt.isPresent()) {
                seed = seedOpt.get();
            } else {
                if (MinecraftClient.getInstance().isOnThread()) {
                    assert client != null;
                    client.openScreen(Atum.getSeedProvider().getWaitingScreen());
                    return;
                } else {
                    // Note: If a mod ever makes AtumCreateWorldScreens in parallel, the next two lines would cause a race condition.
                    seedProvider.waitForSeed();
                    seedOpt = seedProvider.getSeed();
                    if (!seedOpt.isPresent()) {
                        throw new IllegalStateException("No seed found after waiting!");
                    }
                    seed = seedOpt.get();
                }
            }
        } else {
            seed = Atum.config.seed; // Set seed for config screen
        }

        ((IMoreOptionsDialog) moreOptionsDialog).atum$setSeed(seed);

        if (Atum.isRunning()) {
            if (Atum.inDemoMode()) {
                String demoWorldName = Atum.config.attemptTracker.incrementAndGetWorldName(AttemptTracker.Type.DEMO);
                Atum.LOGGER.info("Creating \"{}\" with demo seed...", demoWorldName);
                MinecraftClient.getInstance().createWorld(demoWorldName, MinecraftServer.DEMO_LEVEL_INFO, RegistryTracker.create(), GeneratorOptions.DEMO_CONFIG);
                return;
            }

            // micro optimization, vanilla calls the changed listener twice, once on setText and once on setCursorToEnd
            this.levelNameField.setChangedListener(string -> {});
            this.levelNameField.setText(seed.isEmpty() ? Atum.config.attemptTracker.incrementAndGetWorldName(AttemptTracker.Type.RSG) : Atum.config.attemptTracker.incrementAndGetWorldName(AttemptTracker.Type.SSG));
            this.updateSaveFolderName();

            if (!seed.isEmpty() && Atum.getSeedProvider().shouldShowSeed()) {
                Atum.LOGGER.info("Creating \"{}\" with seed \"{}\"...", this.levelNameField.getText(), seed);
            } else {
                Atum.LOGGER.info("Creating \"{}\"...", this.levelNameField.getText());
            }
            this.createLevel();
        } else {
            if (((IMoreOptionsDialog) this.moreOptionsDialog).atum$isSetSeed()) {
                this.levelNameField.setText(Atum.config.attemptTracker.getWorldName(AttemptTracker.Type.SSG));
            } else {
                this.levelNameField.setText(Atum.config.attemptTracker.getWorldName(AttemptTracker.Type.RSG));
            }
            this.levelNameField.setSelected(false);
            this.levelNameField.setEditable(false);
            this.levelNameField.setFocusUnlocked(false);
            this.levelNameField.active = false;

            this.dataPacksButton.active = this.dataPackTempDir != null;
            this.createLevelButton.setMessage(new TranslatableText("gui.done"));
            this.demoModeButton = this.addButton(new ButtonWidget(
                    this.width / 2 + 5, 151, 150, 20,
                    new TranslatableText("atum.config.demoMode", ScreenTexts.getToggleText(Atum.config.demoMode)),
                    button -> button.setMessage(new TranslatableText("atum.config.demoMode", ScreenTexts.getToggleText(Atum.config.demoMode = !Atum.config.demoMode)))
            ));
            this.demoModeButton.visible = this.moreOptionsOpen;
        }
    }

    @Inject(method = "setMoreOptionsOpen(Z)V", at = @At("TAIL"))
    private void updateLevelNameField(boolean moreOptionsOpen, CallbackInfo ci) {
        if (this.isAtum() && !Atum.isRunning()) {
            if (((IMoreOptionsDialog) this.moreOptionsDialog).atum$isSetSeed()) {
                this.levelNameField.setText(Atum.config.attemptTracker.getWorldName(AttemptTracker.Type.SSG));
            } else {
                this.levelNameField.setText(Atum.config.attemptTracker.getWorldName(AttemptTracker.Type.RSG));
            }
            if (this.demoModeButton != null) {
                this.demoModeButton.visible = moreOptionsOpen;
            }
        }
    }

    @Inject(method = "createLevel", at = @At("HEAD"), cancellable = true)
    private void saveAtumConfigurations(CallbackInfo ci) {
        assert this.client != null;
        if (!this.isAtum() || Atum.isRunning()) {
            return;
        }

        Atum.config.gameMode = this.currentMode;
        Atum.config.difficulty = this.difficulty;
        Atum.config.cheatsEnabled = this.cheatsEnabled;
        Atum.config.setGameRules(this.gameRules.copy());
        Atum.config.setDataPackSettings(this.dataPackSettings);

        ((IMoreOptionsDialog) this.moreOptionsDialog).atum$saveAtumConfigurations();

        if (Atum.config.updateHasLegalSettings()) {
            Atum.config.save();
            this.client.openScreen(this.parent);
        } else {
            this.client.openScreen(new ConfirmScreen(confirm -> {
                if (!confirm) {
                    Atum.config.resetToLegalSettings();
                }
                Atum.config.save();
                this.client.openScreen(this.parent);
            }, new TranslatableText("atum.menu.legal_settings.warning"), Atum.config.getIllegalSettingsWarning(), new TranslatableText("atum.menu.legal_settings.confirm"), new TranslatableText("atum.menu.legal_settings.reset")));
        }

        ci.cancel();
    }

    @WrapWithCondition(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;updateSaveFolderName()V"
            )
    )
    private boolean doNotUpdateEmptySaveFolderName(CreateWorldScreen screen) {
        // micro-optimization, we call updateSaveFolderName ourselves when creating the level
        return !Atum.isRunning();
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;drawStringWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V", ordinal = 0), slice = @Slice(
            from = @At(value = "CONSTANT", args = "stringValue=selectWorld.resultFolder")
    ))
    private boolean doNotShowResultFolderOnConfigScreen(CreateWorldScreen screen, MatrixStack matrices, TextRenderer textRenderer, String string, int x, int y, int color) {
        return !this.isAtum();
    }

    @ModifyExpressionValue(method = "copyDataPack(Ljava/nio/file/Path;Lnet/minecraft/client/MinecraftClient;)Ljava/nio/file/Path;", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"))
    private static Stream<Path> filterAtumDataPacks(Stream<Path> dataPacks, Path directory, MinecraftClient minecraftClient) {
        if (directory.equals(Atum.config.dataPackDirectory)) {
            Set<String> expectedDataPacks = Atum.config.getExpectedDataPacks();
            Set<Path> dataPackPaths = new HashSet<>();

            // instantly collect to bypass lazy evaluation
            dataPacks = dataPacks.filter(path -> {
                // check if datapacks are expected in main directory
                if (path.getParent().equals(directory)) {
                    String dataPackName = path.toString().replace("\\", "/").replaceFirst(Atum.config.dataPackDirectory.toString().replace("\\", "/"), "file");
                    if (expectedDataPacks.remove(dataPackName)) {
                        dataPackPaths.add(path);
                        return true;
                    }
                    return false;
                }
                // check if path belongs to any expected datapack
                for (Path dataPack : dataPackPaths) {
                    if (path.startsWith(dataPack)) {
                        return true;
                    }
                }
                return false;
            }).collect(Collectors.toList()).stream();

            if (!expectedDataPacks.isEmpty()) {
                Atum.config.dataPackMismatch = true;
                Atum.LOGGER.warn("Data pack mismatch, some of the configured files are missing!");
            }
        }
        return dataPacks;
    }

    @Unique
    private boolean isAtum() {
        return (Object) this instanceof AtumCreateWorldScreen;
    }
}
