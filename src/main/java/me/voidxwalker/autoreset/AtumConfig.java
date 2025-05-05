package me.voidxwalker.autoreset;

import com.google.gson.JsonObject;
import me.contaria.speedrunapi.config.SpeedrunConfigContainer;
import me.contaria.speedrunapi.config.api.SpeedrunConfig;
import me.contaria.speedrunapi.config.api.annotations.Config;
import me.contaria.speedrunapi.util.TextUtil;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import me.voidxwalker.autoreset.mixin.access.CreateWorldScreen$ModeAccessor;
import me.voidxwalker.autoreset.mixin.access.GeneratorTypeAccessor;
import me.voidxwalker.autoreset.mixin.access.IntegratedServerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.world.level.LevelGeneratorType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AtumConfig implements SpeedrunConfig {
    @Config.Ignored
    private SpeedrunConfigContainer<?> container;

    public CreateWorldScreen.Mode gameMode = CreateWorldScreen.Mode.SURVIVAL;
    public boolean structures = true;
    @Config.Strings.MaxChars(32)
    public String seed = "";
    public boolean bonusChest = false;
    public boolean cheatsEnabled = false;
    public AtumGeneratorType generatorType = AtumGeneratorType.DEFAULT;
    public String generatorDetails = "";

    public boolean demoMode;

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
            texts.add(I18n.translate("atum.config.demoMode", I18n.translate("options.on")));
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

        debugText.addAll(this.getIllegalSettingsStrings());

        return debugText;
    }

    @Override
    public void finishInitialization(SpeedrunConfigContainer<?> container) {
        this.container = container;
    }

    @Override
    public void onLoad(JsonObject jsonObject, int dataVersion) {
        if (dataVersion < 1) {
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
    public @NotNull Screen createConfigScreen(Screen parent) {
        // isAvailable() already takes care of this, but because it's so important we do another check just to be completely sure Atum is not running when the player opens the Atum config
        if (Atum.isRunning()) {
            throw new IllegalStateException("Cannot configure Atum while it's running.");
        }
        return new AtumCreateWorldScreen(parent, AtumCreateWorldScreen.Job.CONFIGURATION);
    }

    @Override
    public boolean isAvailable() {
        return !Atum.isRunning();
    }

    @SuppressWarnings("unused")
    public enum AtumGeneratorType {
        DEFAULT(GeneratorTypeAccessor.atum$DEFAULT()),
        FLAT(GeneratorTypeAccessor.atum$FLAT()),
        LARGE_BIOMES(GeneratorTypeAccessor.atum$LARGE_BIOMES()),
        AMPLIFIED(GeneratorTypeAccessor.atum$AMPLIFIED()),
        SINGLE_BIOME_SURFACE(GeneratorTypeAccessor.atum$BUFFET()),
        DEBUG(GeneratorTypeAccessor.atum$DEBUG_ALL_BLOCK_STATES());

        private final LevelGeneratorType generatorType;

        AtumGeneratorType(LevelGeneratorType generatorType) {
            this.generatorType = generatorType;
        }

        public LevelGeneratorType get() {
            return this.generatorType;
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
