package me.voidxwalker.autoreset.mixin.config;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.contaria.speedrunapi.util.TextUtil;
import me.voidxwalker.autoreset.AttemptTracker;
import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.AtumConfig;
import me.voidxwalker.autoreset.AtumCreateWorldScreen;
import me.voidxwalker.autoreset.AtumCreateWorldScreen.Job;
import me.voidxwalker.autoreset.api.seedprovider.AtumWaitingScreen;
import me.voidxwalker.autoreset.api.seedprovider.SeedProvider;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.world.level.LevelGeneratorType;
import net.minecraft.world.level.LevelInfo;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
    @Shadow
    @Final
    private Screen parent;

    @Shadow
    private CreateWorldScreen.Mode currentMode;
    @Shadow
    private boolean cheatsEnabled;
    @Shadow
    private boolean tweakedCheats;

    @Shadow
    private boolean moreOptionsOpen;
    @Shadow
    private TextFieldWidget levelNameField;
    @Shadow
    private ButtonWidget createLevelButton;

    @Unique
    private CompletableFuture<String> seedFuture;
    @Unique
    private AbstractButtonWidget demoModeButton;

    @Shadow
    protected abstract void updateSaveFolderName();

    @Shadow
    protected abstract void createLevel();

    @Shadow
    private String seed;
    @Shadow
    private boolean structures;
    @Shadow
    private boolean bonusChest;
    @Shadow
    private int generatorType;

    @Shadow
    private TextFieldWidget seedField;

    @Shadow
    public CompoundTag generatorOptionsTag;

    @Shadow
    private ButtonWidget gameModeSwitchButton;

    protected CreateWorldScreenMixin(Text title) {
        super(title);
    }

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void loadAtumConfigurations(CallbackInfo ci) {
        if (!this.isAtum()) {
            return;
        }

        this.load();
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
                            value = "CONSTANT",
                            args = "stringValue=gui.cancel"
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

        if (this.isAtumReset()) {
            this.continueReset();
            return;
        }

        this.seedField.setText(Atum.config.seed);

        this.initConfigScreen();
    }

    @Unique
    private void continueReset() {
        String seed = this.getSeed();
        if (seed == null) {
            return;
        }

        this.createWorld(seed);
    }

    @Inject(
            method = "setMoreOptionsOpen(Z)V",
            at = @At("TAIL")
    )
    private void updateLevelNameField(boolean moreOptionsOpen, CallbackInfo ci) {
        if (this.isAtumConfig()) {
            this.levelNameField.setText(Atum.config.attemptTracker.getWorldName(
                    !this.seed.isEmpty() ? AttemptTracker.Type.SSG : AttemptTracker.Type.RSG
            ));
            if (this.demoModeButton != null) {
                this.demoModeButton.visible = !moreOptionsOpen;
            }
        }
    }

    @Inject(
            method = "createLevel",
            at = @At("HEAD"),
            cancellable = true
    )
    private void saveAtumConfigurations(CallbackInfo ci) {
        if (!this.isAtumConfig()) {
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
        return !this.isAtumReset();
    }

    @WrapWithCondition(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;drawString(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=selectWorld.resultFolder"
                    )
            )
    )
    private boolean doNotShowResultFolderOnConfigScreen(CreateWorldScreen screen, TextRenderer textRenderer, String s, int x, int y, int color) {
        return !this.isAtum();
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;drawCenteredString(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"
            ),
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;firstGameModeDescriptionLine:Ljava/lang/String;"
                    )
            ),
            index = 3
    )
    private int modifyGameModeDescriptionY(int y) {
        if (this.isAtum()) {
            return y - 15;
        }
        return y;
    }

    @WrapWithCondition(
            method = "createLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MinecraftClient;openScreen(Lnet/minecraft/client/gui/screen/Screen;)V"
            )
    )
    private boolean doNotOpenTitleScreen(MinecraftClient client, Screen screen) {
        return !this.isAtumReset();
    }

    @ModifyArg(
            method = "createLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MinecraftClient;startIntegratedServer(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/level/LevelInfo;)V"
            ),
            index = 2
    )
    private LevelInfo addSeedToLevelInfo(LevelInfo levelInfo) {
        ((ISeedStringHolder) (Object) levelInfo).atum$setSeedString(this.seed);
        return levelInfo;
    }

    @Unique
    private void load() {
        this.currentMode = Atum.config.gameMode;
        this.structures = Atum.config.structures;
        this.cheatsEnabled = Atum.config.cheatsEnabled;
        this.bonusChest = Atum.config.bonusChest;
        this.tweakedCheats = true;

        this.generatorType = Atum.config.generatorType.get().getId();
        this.generatorOptionsTag = this.loadGeneratorDetails(Atum.config.generatorDetails);
    }

    @Unique
    private CompoundTag loadGeneratorDetails(String generatorDetails) {
        if (!generatorDetails.isEmpty()) {
            try {
                return StringNbtReader.parse(generatorDetails);
            } catch (CommandSyntaxException e) {
                Atum.LOGGER.error("Failed to parse generator details!", e);
            }
        }
        return new CompoundTag();
    }

    @Unique
    private @Nullable String getSeed() {
        if (this.isAtumConfig()) {
            return Objects.requireNonNull(Atum.config.seed);
        }
        try {
            SeedProvider seedProvider = Atum.getSeedProvider();
            if (this.seedFuture == null) {
                this.seedFuture = seedProvider.requestSeed();
                Atum.SEED_FUTURES.add(this.seedFuture);
                this.seedFuture.handle((s, throwable) -> Atum.SEED_FUTURES.remove(this.seedFuture));
            }
            if (this.seedFuture.isDone()) {
                // Could do .get() but then we have to handle an extra exception type for no reason, .join() should
                // immediately return because supposedly it isDone.
                return this.seedFuture.join();
            }
            AtumWaitingScreen waitingScreen;
            if (MinecraftClient.getInstance().isOnThread() && (waitingScreen = Atum.getSeedProvider().getWaitingScreen().orElse(null)) != null) {
                // When the waiting screen wants to cancel the seed, cancel the seed future, and then let the tick
                // activity move us back to this screen. Technically this can be a race condition where the seed future
                // completes as the waiting screen wants to cancel it. But this isn't really an issue, as futures are
                // inherently thread-safe so the race won't cause any misbehavior, and cancelling an already completed
                // seed future doesn't cause any issues, and the tick activity will simply reopen this screen and the
                // seed future will resolve to whichever completion won the race.
                waitingScreen.addCancelActivity(() -> this.seedFuture.cancel(true));
                waitingScreen.addTickActivity(() -> {
                    // Move back to this screen once the seed future is done, however it is done.
                    if (this.seedFuture.isDone()) {
                        MinecraftClient.getInstance().openScreen(this);
                    }
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
                this.seedFuture.cancel(true);
                this.seedFuture.join(); // Move to CancellationException block or possibly the other exception block.
                return null;
            }
            return this.seedFuture.join();
        } catch (CancellationException e) {
            Atum.LOGGER.warn("The seed has been cancelled.");
            this.onSeedFutureFail(e);
            return null;
        } catch (CompletionException e) {
            Atum.LOGGER.error("Failed to get seed from the seed provider!", e);
            this.onSeedFutureFail(e);
            return null;
        }
    }

    @Unique
    private void onSeedFutureFail(Throwable ex) {
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
            MinecraftClient.getInstance().startIntegratedServer(demoWorldName, demoWorldName, MinecraftServer.DEMO_LEVEL_INFO);
            return;
        }

        this.seedField.setText(seed);

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
                !this.seed.isEmpty() ? AttemptTracker.Type.SSG : AttemptTracker.Type.RSG
        ));
        this.levelNameField.setSelected(false);
        this.levelNameField.setEditable(false);
        this.levelNameField.setFocusUnlocked(false);
        this.levelNameField.active = false;

        this.createLevelButton.setMessage(I18n.translate("gui.done"));
        this.gameModeSwitchButton.y -= 15;
        this.demoModeButton = this.addButton(new ButtonWidget(
                this.width / 2 - 75, 151, 150, 20,
                I18n.translate("atum.config.demoMode", I18n.translate(Atum.config.demoMode ? "options.on" : "options.off")),
                button -> {
                    Atum.config.demoMode = !Atum.config.demoMode;
                    button.setMessage(I18n.translate("atum.config.demoMode", I18n.translate(Atum.config.demoMode ? "options.on" : "options.off")));
                }
        ));
        this.demoModeButton.visible = !this.moreOptionsOpen;
    }

    @Unique
    private void save() {
        Atum.config.gameMode = this.currentMode;
        Atum.config.structures = this.structures;
        Atum.config.seed = this.seed;
        Atum.config.cheatsEnabled = this.cheatsEnabled;
        Atum.config.bonusChest = this.bonusChest;

        Atum.config.generatorType = AtumConfig.AtumGeneratorType.from(LevelGeneratorType.TYPES[this.generatorType]);
        Atum.config.generatorDetails = this.generatorOptionsTag != null && !this.generatorOptionsTag.isEmpty() ? this.generatorOptionsTag.toString() : "";
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
        }, TextUtil.translatable("atum.menu.legal_settings.warning"), Atum.config.getIllegalSettingsWarning(), I18n.translate("atum.menu.legal_settings.confirm"), I18n.translate("atum.menu.legal_settings.reset")));
    }

    @Unique
    private boolean isAtum() {
        return (Object) this instanceof AtumCreateWorldScreen;
    }

    @SuppressWarnings("DataFlowIssue")
    @Unique
    private Job getJob() {
        return ((AtumCreateWorldScreen) (Object) this).getJob();
    }

    @Unique
    private boolean isAtumConfig() {
        return this.isAtum() && this.getJob() == Job.CONFIGURATION;
    }

    @Unique
    private boolean isAtumReset() {
        return this.isAtum() && this.getJob() == Job.CREATION;
    }
}
