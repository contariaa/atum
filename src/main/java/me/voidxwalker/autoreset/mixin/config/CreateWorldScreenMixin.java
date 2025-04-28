package me.voidxwalker.autoreset.mixin.config;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.contaria.speedrunapi.util.TextUtil;
import me.voidxwalker.autoreset.AttemptTracker;
import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.AtumCreateWorldScreen;
import me.voidxwalker.autoreset.AtumCreateWorldScreen.Job;
import me.voidxwalker.autoreset.api.seedprovider.AtumWaitingScreen;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
    private CompletableFuture<String> seedFuture;
    @Unique
    private AbstractButtonWidget demoModeButton;

    @Shadow
    protected abstract void updateSaveFolderName();

    @Shadow
    protected abstract void createLevel();

    protected CreateWorldScreenMixin(Text title) {
        super(title);
    }

    @Inject(
            method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/gui/screen/world/MoreOptionsDialog;)V",
            at = @At("TAIL")
    )
    private void loadAtumConfigurations(CallbackInfo ci) {
        if (!this.isAtum()) {
            return;
        }

        this.load();
        this.initDataPacks();
    }

    @WrapWithCondition(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;addButton(Lnet/minecraft/client/gui/widget/AbstractButtonWidget;)Lnet/minecraft/client/gui/widget/AbstractButtonWidget;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/client/gui/screen/ScreenTexts;CANCEL:Lnet/minecraft/text/Text;"
                    )
            )
    )
    private boolean removeCancelButton(CreateWorldScreen screen, AbstractButtonWidget button) {
        return !this.isAtum();
    }

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void modifyAtumCreateWorldScreen(CallbackInfo info) {
        if (!this.isAtum()) {
            return;
        }

        String seed = this.getSeed();
        if (seed == null) {
            return;
        }
        ((IMoreOptionsDialog) this.moreOptionsDialog).atum$setSeed(seed);

        if (isAtumReset()) {
            this.createWorld(seed);
            return;
        }

        this.initConfigScreen();
    }

    @Inject(
            method = "setMoreOptionsOpen(Z)V",
            at = @At("TAIL")
    )
    private void updateLevelNameField(boolean moreOptionsOpen, CallbackInfo ci) {
        if (isAtumConfig()) {
            this.levelNameField.setText(Atum.config.attemptTracker.getWorldName(
                    ((IMoreOptionsDialog) this.moreOptionsDialog).atum$isSetSeed() ? AttemptTracker.Type.SSG : AttemptTracker.Type.RSG
            ));
            if (this.demoModeButton != null) {
                this.demoModeButton.visible = moreOptionsOpen;
            }
        }
    }

    @Inject(
            method = "createLevel",
            at = @At("HEAD"),
            cancellable = true
    )
    private void saveAtumConfigurations(CallbackInfo ci) {
        if (!isAtumConfig()) {
            return;
        }

        this.save();
        this.closeConfigScreen();

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
        return !isAtumReset();
    }

    @WrapWithCondition(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;drawStringWithShadow(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=selectWorld.resultFolder"
                    )
            )
    )
    private boolean doNotShowResultFolderOnConfigScreen(CreateWorldScreen screen, MatrixStack matrices, TextRenderer textRenderer, String string, int x, int y, int color) {
        return !this.isAtum();
    }

    @ModifyExpressionValue(
            method = "copyDataPack(Ljava/nio/file/Path;Lnet/minecraft/client/MinecraftClient;)Ljava/nio/file/Path;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"
            )
    )
    private static Stream<Path> filterAtumDataPacks(Stream<Path> dataPacks, Path directory, MinecraftClient client) {
        if (!directory.equals(Atum.config.dataPackDirectory)) {
            return dataPacks;
        }

        Set<String> expected = Atum.config.getExpectedDataPacks();
        Set<Path> paths = new HashSet<>();

        // instantly collect to bypass lazy evaluation
        dataPacks = dataPacks.filter(path -> {
            // check if datapacks are expected in main directory
            if (path.getParent().equals(directory)) {
                String name = "file/" + path.relativize(Atum.config.dataPackDirectory);
                if (expected.remove(name)) {
                    paths.add(path);
                    return true;
                }
                return false;
            }
            // check if path belongs to any expected datapack
            for (Path dataPack : paths) {
                if (path.startsWith(dataPack)) {
                    return true;
                }
            }
            return false;
        }).collect(Collectors.toList()).stream();

        if (!expected.isEmpty()) {
            Atum.config.dataPackMismatch = true;
            Atum.LOGGER.warn("Data pack mismatch, some of the configured files are missing!");
        }
        return dataPacks;
    }

    @Unique
    private void load() {
        this.currentMode = Atum.config.gameMode;
        this.safeDifficulty = this.difficulty = Atum.config.difficulty;
        this.cheatsEnabled = Atum.config.cheatsEnabled;
        this.tweakedCheats = true;
        if (Atum.config.hasModifiedGameRules()) {
            this.gameRules.setAllValues(Atum.config.gameRules, null);
        }
        this.dataPackSettings = new DataPackSettings(Atum.config.dataPackSettings.getEnabled(), Atum.config.dataPackSettings.getDisabled());

        ((IMoreOptionsDialog) this.moreOptionsDialog).atum$loadAtumConfigurations();
    }

    @Unique
    private void initDataPacks() {
        if (isAtumConfig()) {
            this.dataPackTempDir = Atum.config.dataPackDirectory;
            return;
        }

        if (Atum.config.isDefaultDataPackSettings(this.dataPackSettings)) {
            return;
        }

        if (!Files.isDirectory(Atum.config.dataPackDirectory)) {
            Atum.config.dataPackMismatch = true;
            Atum.LOGGER.warn("Data pack mismatch, the Atum data pack directory is missing!");
            return;
        }

        this.dataPackTempDir = CreateWorldScreen.copyDataPack(Atum.config.dataPackDirectory, this.client);
        if (this.dataPackTempDir == null) {
            Atum.config.dataPackMismatch = true;
            Atum.LOGGER.warn("Data pack mismatch, failed to copy data packs!");
        }
    }

    @Unique
    private @Nullable String getSeed() {
        if (isAtumConfig()) {
            return Objects.requireNonNull(Atum.config.seed);
        }
        assert client != null;
        try {
            SeedProvider seedProvider = Atum.getSeedProvider();
            if (seedFuture == null) {
                seedFuture = seedProvider.requestSeed();
                Atum.SEED_FUTURES.add(seedFuture);
                seedFuture.handle((s, throwable) -> Atum.SEED_FUTURES.remove(seedFuture));
            }
            if (seedFuture.isDone()) {
                // Could do .get() but then we have to handle an extra exception type for no reason, .join() should
                // immediately return because supposedly it isDone.
                return seedFuture.join();
            }
            AtumWaitingScreen waitingScreen;
            if (MinecraftClient.getInstance().isOnThread() && (waitingScreen = Atum.getSeedProvider().getWaitingScreen().orElse(null)) != null) {
                // When the waiting screen wants to cancel the seed, cancel the seed future, and then let the tick
                // activity move us back to this screen. Technically this can be a race condition where the seed future
                // completes as the waiting screen wants to cancel it. But this isn't really an issue, as futures are
                // inherently thread-safe so the race won't cause any misbehavior, and cancelling an already completed
                // seed future doesn't cause any issues, and the tick activity will simply reopen this screen and the
                // seed future will resolve to whichever completion won the race.
                waitingScreen.addCancelActivity(() -> seedFuture.cancel(true));
                waitingScreen.addTickActivity(() -> {
                    // Move back to this screen once the seed future is done, however it is done.
                    if (seedFuture.isDone()) client.openScreen(this);
                });
                MinecraftClient.getInstance().openScreen(waitingScreen);
                return null;
            }
            // Job is already CREATION, we need to make sure we check atum is still running to prevent race conditions
            // with mods that create worlds on other threads (seedqueue).
            if (!Atum.isRunning()) {
                // Atum.stopRunning() may have ran right before this seedFuture was added to SEED_FUTURES, so if atum
                // stopped running mid world creation, we should cancel this seed future to make sure this one is also
                // caught.
                seedFuture.cancel(true);
                seedFuture.join(); // Move to CancellationException block or possibly the other exception block.
                return null;
            }
            return seedFuture.join();
        } catch (CancellationException e) {
            Atum.LOGGER.warn("The seed has been cancelled.");
            onSeedFutureFail(e);
            return null;
        } catch (CompletionException e) {
            Atum.LOGGER.error("Failed to get seed from the seed provider!", e);
            onSeedFutureFail(e);
            return null;
        }
    }

    @Unique
    private void onSeedFutureFail(Throwable ex) {
        assert client != null;
        Atum.cancelAllSeeds();
        Atum.SEED_FAILURES.add(ex);
        if (MinecraftClient.getInstance().isOnThread()) {
            Atum.checkSeedFailures();
        }
    }

    @Unique
    private void createWorld(String seed) {
        if (Atum.inDemoMode()) {
            String demoWorldName = Atum.config.attemptTracker.incrementAndGetWorldName(AttemptTracker.Type.DEMO);
            Atum.LOGGER.info("Creating \"{}\" with demo seed...", demoWorldName);
            MinecraftClient.getInstance().createWorld(demoWorldName, MinecraftServer.DEMO_LEVEL_INFO, RegistryTracker.create(), GeneratorOptions.DEMO_CONFIG);
            return;
        }

        // micro optimization, vanilla calls the changed listener twice,
        // once on setText and once on setCursorToEnd
        this.levelNameField.setChangedListener(string -> {
        });
        this.levelNameField.setText(
                Atum.config.attemptTracker.incrementAndGetWorldName(seed.isEmpty() ? AttemptTracker.Type.RSG : AttemptTracker.Type.SSG)
        );
        this.updateSaveFolderName();

        if (!seed.isEmpty() && Atum.getSeedProvider().shouldShowSeed()) {
            Atum.LOGGER.info("Creating \"{}\" with seed \"{}\"...", this.levelNameField.getText(), seed);
        } else {
            Atum.LOGGER.info("Creating \"{}\"...", this.levelNameField.getText());
        }
        this.createLevel();
    }

    @Unique
    private void initConfigScreen() {
        this.levelNameField.setText(Atum.config.attemptTracker.getWorldName(
                ((IMoreOptionsDialog) this.moreOptionsDialog).atum$isSetSeed() ? AttemptTracker.Type.SSG : AttemptTracker.Type.RSG
        ));
        this.levelNameField.setSelected(false);
        this.levelNameField.setEditable(false);
        this.levelNameField.setFocusUnlocked(false);
        this.levelNameField.active = false;

        this.dataPacksButton.active = this.dataPackTempDir != null;
        this.createLevelButton.setMessage(TextUtil.translatable("gui.done"));
        this.demoModeButton = this.addButton(new ButtonWidget(
                this.width / 2 + 5, 151, 150, 20,
                TextUtil.translatable("atum.config.demoMode", ScreenTexts.getToggleText(Atum.config.demoMode)),
                button -> button.setMessage(TextUtil.translatable("atum.config.demoMode", ScreenTexts.getToggleText(Atum.config.demoMode = !Atum.config.demoMode)))
        ));
        this.demoModeButton.visible = this.moreOptionsOpen;
    }

    @Unique
    private void save() {
        Atum.config.gameMode = this.currentMode;
        Atum.config.difficulty = this.difficulty;
        Atum.config.cheatsEnabled = this.cheatsEnabled;
        Atum.config.setGameRules(this.gameRules.copy());
        Atum.config.setDataPackSettings(this.dataPackSettings);

        ((IMoreOptionsDialog) this.moreOptionsDialog).atum$saveAtumConfigurations();
    }

    @Unique
    private void closeConfigScreen() {
        if (Atum.config.updateHasLegalSettings()) {
            Atum.config.save();
            MinecraftClient.getInstance().openScreen(this.parent);
            return;
        }
        MinecraftClient.getInstance().openScreen(new ConfirmScreen(confirm -> {
            if (!confirm) {
                Atum.config.resetToLegalSettings();
            }
            Atum.config.save();
            MinecraftClient.getInstance().openScreen(this.parent);
        }, TextUtil.translatable("atum.menu.legal_settings.warning"), Atum.config.getIllegalSettingsWarning(), TextUtil.translatable("atum.menu.legal_settings.confirm"), TextUtil.translatable("atum.menu.legal_settings.reset")));
    }

    @Unique
    private boolean isAtum() {
        return (Object) this instanceof AtumCreateWorldScreen;
    }

    @SuppressWarnings("all")
    @Unique
    private boolean isAtumConfig() {
        return isAtum() && ((AtumCreateWorldScreen) (Object) this).getJob() == Job.CONFIGURATION;
    }

    @SuppressWarnings("all")
    @Unique
    private boolean isAtumReset() {
        return isAtum() && ((AtumCreateWorldScreen) (Object) this).getJob() == Job.CREATION;
    }
}
