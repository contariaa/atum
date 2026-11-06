package me.voidxwalker.autoreset;

import com.google.gson.JsonObject;
import me.contaria.speedrunapi.config.SpeedrunConfigAPI;
import me.contaria.speedrunapi.config.SpeedrunConfigContainer;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.SpeedrunConfigParsedMetadata;
import me.contaria.speedrunapi.config.api.SpeedrunOption;
import me.contaria.speedrunapi.config.api.annotations.Config;
import me.contaria.speedrunapi.config.api.gui.CallbackButtonWidget;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import me.voidxwalker.autoreset.mixin.access.IntegratedServerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelGeneratorType;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AtumConfig implements SpeedrunConfig {
    @Config.Ignored
    private SpeedrunConfigContainer<?> container;

    @Config.Hide
    public String gameMode = "survival";
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
                    .createWidget((option, config_, configStorage, optionField) -> new CallbackButtonWidget(0, 0, 150, 20, I18n.translate("atum.menu.configure"), button -> {
                        // isAvailable() already takes care of this, but because it's so important we do another check just to be completely sure Atum is not running when the player opens the Atum config
                        if (Atum.isRunning()) {
                            throw new IllegalStateException("Cannot configure Atum while it's running.");
                        }
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.setScreen(new AtumCreateWorldScreen(client.currentScreen, AtumCreateWorldScreen.Job.CONFIGURATION, false));
                    }))
                    .build();
        }
        return SpeedrunConfig.super.parseField(field, config, idPrefix);
    }

    public boolean updateHasLegalSettings() {
        return this.hasLegalSettings = (this.gameMode.equals("survival") || this.gameMode.equals("hardcore")) &&
                this.structures &&
                !this.bonusChest &&
                !this.cheatsEnabled &&
                this.generatorType == AtumGeneratorType.DEFAULT &&
                !this.demoMode;
    }

    public String getIllegalSettingsWarning() {
        List<String> warnings = this.getIllegalSettingsStrings();
        if (warnings.isEmpty()) {
            return "gui.none";
        }
        StringBuilder warning = new StringBuilder(warnings.remove(0));
        for (String w : warnings) {
            warning.append(", ").append(w);
        }
        return warning.toString();
    }

    private List<String> getIllegalSettingsStrings() {
        List<String> texts = new ArrayList<>();
        if (!this.gameMode.equals("survival") && !this.gameMode.equals("hardcore")) {
            texts.add(I18n.translate("selectWorld.gameMode") + ": " + I18n.translate("selectWorld.gameMode." + this.gameMode));
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
        if (!this.gameMode.equals("hardcore")) {
            this.gameMode = "survival";
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

        if (!this.gameMode.equals("survival") && !this.gameMode.equals("hardcore")) {
            debugText.add("Game Mode: " + this.gameMode.substring(0, 1).toUpperCase(Locale.ROOT) + this.gameMode.substring(1).toLowerCase(Locale.ROOT));
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
        if (this.demoMode) {
            debugText.add("Demo Mode: ON");
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
        DEFAULT(LevelGeneratorType.DEFAULT, "Default"),
        FLAT(LevelGeneratorType.FLAT, "Superflat"),
        LARGE_BIOMES(LevelGeneratorType.LARGE_BIOMES, "Large Biomes"),
        AMPLIFIED(LevelGeneratorType.AMPLIFIED, "AMPLIFIED"),
        CUSTOMIZED(LevelGeneratorType.CUSTOMIZED, "Customized"),
        DEBUG(LevelGeneratorType.DEBUG, "Debug Mode");

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
