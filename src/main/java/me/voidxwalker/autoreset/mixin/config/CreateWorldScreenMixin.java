package me.voidxwalker.autoreset.mixin.config;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.contaria.speedrunapi.util.TextUtil;
import me.voidxwalker.autoreset.AttemptTracker;
import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.AtumConfig;
import me.voidxwalker.autoreset.AtumCreateWorldScreen;
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
import java.util.Optional;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
    @Shadow
    @Final
    private Screen parent;

    @Shadow
    private String gameMode;
    @Shadow
    private boolean field_3178; // isHardcore
    @Shadow
    private String seed;
    @Shadow
    private boolean cheatsEnabled;
    @Shadow
    private boolean field_3179; // tweakedCheats
    @Shadow
    private boolean structures;
    @Shadow
    private boolean bonusChest;
    @Shadow
    private int generatorType;
    @Shadow
    public CompoundTag generatorOptionsTag;

    @Shadow
    private boolean field_3202; // moreOptionsOpen
    @Shadow
    private TextFieldWidget levelNameField;
    @Shadow
    private ButtonWidget createLevelButton;
    @Shadow
    private TextFieldWidget seedField;
    @Shadow
    private ButtonWidget gameModeSwitchButton;

    @Unique
    private AbstractButtonWidget demoModeButton;

    @Shadow
    protected abstract void method_2727(); // updateSaveFolderName

    @Shadow
    protected abstract void createLevel();

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

        String seed = this.getSeed();
        if (seed == null) {
            return;
        }
        this.seedField.setText(seed);

        if (Atum.isRunning()) {
            this.createWorld(seed);
            return;
        }

        this.initConfigScreen();
    }

    @Inject(
            method = "method_2710",
            at = @At("TAIL")
    )
    private void updateLevelNameField(boolean moreOptionsOpen, CallbackInfo ci) {
        if (!Atum.isRunning() && this.isAtum()) {
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
        if (!this.isAtum() || Atum.isRunning()) {
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
                    target = "Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;method_2727()V"
            )
    )
    private boolean doNotUpdateEmptySaveFolderName(CreateWorldScreen screen) {
        // micro-optimization, we call updateSaveFolderName ourselves when creating the level
        return !Atum.isRunning();
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
                            target = "Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;field_3194:Ljava/lang/String;"
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
        return !Atum.isRunning();
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
        this.gameMode = Atum.config.gameMode;
        this.field_3178 = this.gameMode.equals("hardcore");
        this.structures = Atum.config.structures;
        this.cheatsEnabled = Atum.config.cheatsEnabled;
        this.bonusChest = Atum.config.bonusChest;
        this.field_3179 = true;

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
        if (!Atum.isRunning()) {
            return Objects.requireNonNull(Atum.config.seed);
        }
        SeedProvider seedProvider = Atum.getSeedProvider();
        Optional<String> seed = seedProvider.getSeed();
        if (seed.isPresent()) {
            return seed.get();
        }
        if (MinecraftClient.getInstance().isOnThread()) {
            MinecraftClient.getInstance().openScreen(Atum.getSeedProvider().getWaitingScreen());
            return null;
        }
        // Note: If a mod ever makes AtumCreateWorldScreens in parallel, the next two lines would cause a race condition.
        seedProvider.waitForSeed();
        return seedProvider.getSeed().orElseThrow(() -> new IllegalStateException("No seed found after waiting!"));
    }

    @Unique
    private void createWorld(String seed) {
        if (Atum.inDemoMode()) {
            String demoWorldName = Atum.config.attemptTracker.incrementAndGetWorldName(AttemptTracker.Type.DEMO);
            Atum.LOGGER.info("Creating \"{}\" with demo seed...", demoWorldName);
            MinecraftClient.getInstance().startIntegratedServer(demoWorldName, demoWorldName, MinecraftServer.DEMO_LEVEL_INFO);
            return;
        }

        // micro optimization, vanilla calls the changed listener twice,
        // once on setText and once on setCursorToEnd
        this.levelNameField.setChangedListener(string -> {
        });
        this.levelNameField.setText(
                Atum.config.attemptTracker.incrementAndGetWorldName(seed.isEmpty() ? AttemptTracker.Type.RSG : AttemptTracker.Type.SSG)
        );
        this.method_2727();

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
        this.levelNameField.method_1876(false);
        this.levelNameField.setEditable(false);
        this.levelNameField.method_1856(false);
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
        this.demoModeButton.visible = !this.field_3202;
    }

    @Unique
    private void save() {
        Atum.config.gameMode = this.gameMode;
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
        }, TextUtil.literal(I18n.translate("atum.menu.legal_settings.warning")), Atum.config.getIllegalSettingsWarning(), I18n.translate("atum.menu.legal_settings.confirm"), I18n.translate("atum.menu.legal_settings.reset")));
    }

    @Unique
    private boolean isAtum() {
        return (Object) this instanceof AtumCreateWorldScreen;
    }
}
