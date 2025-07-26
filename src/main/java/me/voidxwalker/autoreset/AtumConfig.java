package me.voidxwalker.autoreset;

import com.google.gson.JsonObject;
import me.contaria.speedrunapi.config.SpeedrunConfigAPI;
import me.contaria.speedrunapi.config.SpeedrunConfigContainer;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunConfigParsedMetadata;
import me.contaria.speedrunapi.config.api.SpeedrunOption;
import me.contaria.speedrunapi.config.api.annotations.Config;
import me.contaria.speedrunapi.util.TextUtil;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import me.voidxwalker.autoreset.mixin.access.CreateWorldScreen$ModeAccessor;
import me.voidxwalker.autoreset.mixin.access.GeneratorTypeAccessor;
import me.voidxwalker.autoreset.mixin.access.IntegratedServerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.world.level.LevelGeneratorType;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AtumConfig implements SpeedrunConfig {
    @Config.Ignored
    private SpeedrunConfigContainer<?> container;

    @Config.Hide
    public CreateWorldScreen.Mode gameMode = CreateWorldScreen.Mode.SURVIVAL;
    @Config.Hide
    public boolean structures = true;
    @Config.Hide
    @Config.Strings.MaxChars(32)
    public String seed = "";
    @Config.Hide
    public boolean bonusChest = false;
    @Config.Hide
    public boolean cheatsEnabled = false;
    @Config.Hide
    public AtumGeneratorType generatorType = AtumGeneratorType.DEFAULT;
    @Config.Hide
    public String generatorDetails = "";

    @SuppressWarnings("unused") // for button
    public Void worldGeneration;
    public boolean demoMode = false;
    public boolean hotkeyOnly = false;
    public boolean safeHotkey = true;
    public boolean illegalSettingsWarning = true;

    @Config.Hide
    @SuppressWarnings({"unused", "FieldCanBeLocal"}) // saved to config for PaceMan
    private boolean hasLegalSettings;

    @Config.Ignored
    public AttemptTracker attemptTracker = new AttemptTracker();

    {
        Atum.config = this;
    }

    public AtumConfig() throws IOException {
    }

    public boolean isSetSeed() {
        return !this.seed.isEmpty();
    }

    public void save() {
        try {
            this.container.save();
        } catch (IOException e) {
            Atum.LOGGER.warn("Failed to save Atum config.");
        }
    }

    @Override
    public @Nullable SpeedrunOption<?> parseField(Field field, SpeedrunConfig config, String... idPrefix) {
        if ("worldGeneration".equals(field.getName())) {
            return new SpeedrunConfigAPI.CustomOption.Builder<>(this, this, field, idPrefix)
                    .fromJson((option, config_, configStorage, optionField, jsonElement) -> {})
                    .toJson((option, config_, configStorage, optionField) -> null)
                    .createWidget((option, config_, configStorage, optionField) -> new ButtonWidget(0, 0, 150, 20, I18n.translate("atum.menu.configure"), button -> {
                        // isAvailable() already takes care of this, but because it's so important we do another check just to be completely sure Atum is not running when the player opens the Atum config
                        if (Atum.isRunning()) {
                            throw new IllegalStateException("Cannot configure Atum while it's running.");
                        }
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.openScreen(new AtumCreateWorldScreen(client.currentScreen, AtumCreateWorldScreen.Job.CONFIGURATION, false));
                    }))
                    .build();
        }
        return SpeedrunConfig.super.parseField(field, config, idPrefix);
    }

    @Override
    public void preSave() {
        // fallback in case the player atum resets from the config screen and onConfigScreenClose isn't called
        this.updateHasLegalSettings();
    }

    @Override
    public void onConfigScreenClose(Screen current, Screen parent) {
        if (this.updateHasLegalSettings()) {
            return;
        }
        if (Atum.config.illegalSettingsWarning) {
            MinecraftClient.getInstance().openScreen(this.createConfirmScreen(parent));
        }
    }

    public ConfirmScreen createConfirmScreen(Screen parent) {
        return new ConfirmScreen(confirm -> {
            if (!confirm) {
                Atum.config.resetToLegalSettings();
            }
            Atum.config.save();
            MinecraftClient.getInstance().openScreen(parent);
        }, TextUtil.translatable("atum.menu.legal_settings.warning"), Atum.config.getIllegalSettingsWarning(), I18n.translate("atum.menu.legal_settings.confirm"), I18n.translate("atum.menu.legal_settings.reset")) {
            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                // ConfirmScreen takes the false callback if esc is pressed
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    return false;
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        };
    }

    public boolean updateHasLegalSettings() {
        return this.hasLegalSettings = (this.gameMode == CreateWorldScreen.Mode.SURVIVAL || this.gameMode == CreateWorldScreen.Mode.HARDCORE) &&
                this.structures &&
                !this.bonusChest &&
                !this.cheatsEnabled &&
                this.generatorType == AtumGeneratorType.DEFAULT &&
                !this.demoMode;
    }

    public Text getIllegalSettingsWarning() {
        List<String> warnings = this.getIllegalSettingsStrings();
        if (warnings.isEmpty()) {
            return TextUtil.translatable("gui.none");
        }
        StringBuilder warning = new StringBuilder(warnings.remove(0));
        for (String w : warnings) {
            warning.append(", ").append(w);
        }
        return TextUtil.literal(warning.toString());
    }

    private List<String> getIllegalSettingsStrings() {
        List<String> texts = new ArrayList<>();
        if (this.gameMode != CreateWorldScreen.Mode.SURVIVAL && this.gameMode != CreateWorldScreen.Mode.HARDCORE) {
            texts.add(I18n.translate("selectWorld.gameMode") + ": " + I18n.translate("selectWorld.gameMode." + ((CreateWorldScreen$ModeAccessor) (Object) this.gameMode).atum$getTranslationSuffix()));
        }
        if (this.cheatsEnabled) {
            texts.add(I18n.translate("selectWorld.allowCommands") + " " + I18n.translate("options.on"));
        }
        if (!this.structures) {
            texts.add(I18n.translate("selectWorld.mapFeatures") + " " + I18n.translate("options.off"));
        }
        if (this.bonusChest) {
            texts.add(I18n.translate("selectWorld.bonusItems") + " " + I18n.translate("options.on"));
        }
        if (this.generatorType != AtumGeneratorType.DEFAULT) {
            texts.add(I18n.translate("selectWorld.mapType") + " " + I18n.translate(this.generatorType.get().getTranslationKey()));
        }
        if (this.demoMode) {
            texts.add(I18n.translate("speedrunapi.config.atum.option.demoMode", I18n.translate("options.on")));
        }
        return texts;
    }

    public void resetToLegalSettings() {
        if (this.gameMode != CreateWorldScreen.Mode.HARDCORE) {
            this.gameMode = CreateWorldScreen.Mode.SURVIVAL;
        }
        this.structures = true;
        this.bonusChest = false;
        this.cheatsEnabled = false;
        this.generatorType = AtumGeneratorType.DEFAULT;
        this.generatorDetails = "";
        this.demoMode = false;
    }

    public List<String> getDebugText() {
        List<String> debugText = new ArrayList<>();

        debugText.add("");

        if (Atum.inDemoMode()) {
            debugText.add("Resetting the demo seed");
            return debugText;
        }

        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server != null) {
            String seedLine;
            String creationSeed = ((ISeedStringHolder) (Object) ((IntegratedServerAccessor) server).atum$getLevelInfo()).atum$getSeedString();
            if (!creationSeed.isEmpty()) {
                if (Atum.getSeedProvider().shouldShowSeed()) {
                    seedLine = "Resetting the seed \"" + creationSeed + "\"";
                } else {
                    seedLine = "Resetting a set seed";
                }
            } else {
                seedLine = "Resetting a random seed";
            }
            debugText.add(seedLine);
        }

        if (this.gameMode != CreateWorldScreen.Mode.SURVIVAL && this.gameMode != CreateWorldScreen.Mode.HARDCORE) {
            debugText.add("Game Mode: " + this.gameMode.name().substring(0, 1).toUpperCase(Locale.ROOT) + this.gameMode.name().substring(1).toLowerCase(Locale.ROOT));
        }
        if (this.cheatsEnabled) {
            debugText.add("Allow Cheats: ON");
        }
        if (!this.structures) {
            debugText.add("Generate Structures: OFF");
        }
        if (this.bonusChest) {
            debugText.add("Bonus Chest: ON");
        }
        if (this.generatorType != AtumGeneratorType.DEFAULT) {
            String generatorInformation = this.generatorType.getName();
            if (!this.generatorDetails.isEmpty()) {
                generatorInformation += " (" + this.generatorDetails.hashCode() + ")";
            }
            debugText.add("World Type: " + generatorInformation);
        }

        return debugText;
    }

    @Override
    public void finishInitialization(SpeedrunConfigContainer<?> container) {
        this.container = container;
    }

    @Override
    public void onLoad(JsonObject jsonObject, SpeedrunConfigParsedMetadata metadata) {
        if (metadata.getDataVersion() < 1) {
            // when Atum 2.0 released, default difficulty was set to NORMAL
            // in Atum 2.1, it was set to EASY and the name was changed to
            // "worldDifficulty" to reset the value in everyone's config
            jsonObject.remove("difficulty");
            if (jsonObject.has("worldDifficulty")) {
                jsonObject.add("difficulty", jsonObject.get("worldDifficulty"));
            }
        }
    }

    @Override
    public void finishLoading() {
        this.updateHasLegalSettings();
    }

    @Override
    public int getDataVersion() {
        return 1;
    }

    @Override
    public String modID() {
        return "atum";
    }

    @Override
    public boolean isAvailable() {
        return !Atum.isRunning();
    }

    @SuppressWarnings("unused")
    public enum AtumGeneratorType {
        DEFAULT(GeneratorTypeAccessor.atum$DEFAULT(), "Default"),
        FLAT(GeneratorTypeAccessor.atum$FLAT(), "Superflat"),
        LARGE_BIOMES(GeneratorTypeAccessor.atum$LARGE_BIOMES(), "Large Biomes"),
        AMPLIFIED(GeneratorTypeAccessor.atum$AMPLIFIED(), "AMPLIFIED"),
        SINGLE_BIOME_SURFACE(GeneratorTypeAccessor.atum$BUFFET(), "Buffet"),
        DEBUG(GeneratorTypeAccessor.atum$DEBUG_ALL_BLOCK_STATES(), "Debug Mode");

        private final LevelGeneratorType generatorType;
        private final String name;

        AtumGeneratorType(LevelGeneratorType generatorType, String name) {
            this.generatorType = generatorType;
            this.name = name;
        }

        public LevelGeneratorType get() {
            return this.generatorType;
        }

        public String getName() {
            return this.name;
        }

        public static @Nullable AtumGeneratorType from(LevelGeneratorType generatorType) {
            for (AtumGeneratorType atumGeneratorType : values()) {
                if (atumGeneratorType.get() == generatorType) {
                    return atumGeneratorType;
                }
            }
            return null;
        }
    }
}
