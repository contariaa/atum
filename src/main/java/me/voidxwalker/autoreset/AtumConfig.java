package me.voidxwalker.autoreset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
import me.voidxwalker.autoreset.mixin.access.RuleAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.world.GeneratorType;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AtumConfig implements SpeedrunConfig {
    @Config.Ignored
    private SpeedrunConfigContainer<?> container;

    public CreateWorldScreen.Mode gameMode = CreateWorldScreen.Mode.SURVIVAL;
    public boolean structures = true;
    // renamed from difficulty to worldDifficulty in 2.1
    // 2.0 set the default to NORMAL, causing people to play on normal instead of easy because they weren't used to it
    public Difficulty difficulty = Difficulty.EASY;
    @Config.Strings.MaxChars(32)
    public String seed = "";
    public boolean bonusChest = false;
    public boolean cheatsEnabled;
    public AtumGeneratorType generatorType = AtumGeneratorType.DEFAULT;
    public String generatorDetails = "";
    @Config.Access(setter = "setGameRules")
    public GameRules gameRules = new GameRules();
    @Config.Access(setter = "setDataPackSettings")
    public DataPackSettings dataPackSettings = DataPackSettings.SAFE_MODE;

    public boolean demoMode;

    @SuppressWarnings({"unused", "FieldCanBeLocal"}) // saved to config for PaceMan
    private boolean hasLegalSettings;

    @Config.Ignored
    private boolean modifiedGameRules;

    @Config.Ignored
    public boolean dataPackMismatch;
    @Config.Ignored
    public final Path dataPackDirectory = SpeedrunConfigAPI.getConfigDir().resolve("atum").resolve("datapacks");

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

    public void setGameRules(GameRules gameRules) {
        this.gameRules = gameRules;
        this.modifiedGameRules = this.areGameRulesModified(gameRules);
    }

    public boolean hasModifiedGameRules() {
        return this.modifiedGameRules;
    }

    private boolean areGameRulesModified(GameRules gameRules) {
        GameRules defaultGameRules = new GameRules();
        MutableBoolean modified = new MutableBoolean();
        GameRules.accept(new GameRules.Visitor() {
            @Override
            public <T extends GameRules.Rule<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                if (modified.isFalse() && gameRules.get(key).getCommandResult() != defaultGameRules.get(key).getCommandResult()) {
                    modified.setTrue();
                }
            }
        });
        return modified.booleanValue();
    }

    private JsonElement serializeGameRules(GameRules gameRules) {
        GameRules defaultGameRules = new GameRules();
        JsonObject jsonObject = new JsonObject();
        GameRules.accept(new GameRules.Visitor() {
            @Override
            public <T extends GameRules.Rule<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                GameRules.Rule<T> rule = gameRules.get(key);
                if (rule.getCommandResult() != defaultGameRules.get(key).getCommandResult()) {
                    jsonObject.add(key.getName(), new JsonPrimitive(rule.serialize()));
                }
            }
        });
        return jsonObject;
    }

    private GameRules deserializeGameRules(JsonElement jsonElement) {
        GameRules gameRules = new GameRules();
        GameRules.accept(new GameRules.Visitor() {
            @Override
            public <T extends GameRules.Rule<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                if (jsonElement.getAsJsonObject().has(key.getName())) {
                    ((RuleAccessor) gameRules.get(key)).atum$deserialize(jsonElement.getAsJsonObject().get(key.getName()).getAsString());
                }
            }
        });
        return gameRules;
    }

    public void setDataPackSettings(DataPackSettings dataPackSettings) {
        this.dataPackSettings = this.validateDataPackSettings(dataPackSettings);
    }

    private DataPackSettings validateDataPackSettings(DataPackSettings dataPackSettings) {
        List<String> enabled = new ArrayList<>(dataPackSettings.getEnabled());
        List<String> disabled = new ArrayList<>(dataPackSettings.getDisabled());

        // remove all fabric mod data packs, then re-add them right behind the vanilla pack
        int vanillaIndex = enabled.indexOf("vanilla");
        vanillaIndex = vanillaIndex == -1 ? 0 : vanillaIndex;
        enabled.removeAll(DataPackSettings.SAFE_MODE.getEnabled());
        enabled.removeIf(dataPack -> dataPack.startsWith("fabric/"));
        enabled.addAll(vanillaIndex, DataPackSettings.SAFE_MODE.getEnabled());

        // remove duplicates
        enabled = enabled.stream().distinct().collect(Collectors.toList());
        disabled = disabled.stream().distinct().collect(Collectors.toList());

        disabled.removeAll(enabled);

        dataPackSettings = new DataPackSettings(enabled, disabled);

        if (this.isDefaultDataPackSettings(dataPackSettings)) {
            return DataPackSettings.SAFE_MODE;
        }
        return dataPackSettings;
    }

    // When porting to versions with experimental data packs included, this might need some changes
    public boolean isDefaultDataPackSettings(DataPackSettings dataPackSettings) {
        if (DataPackSettings.SAFE_MODE == dataPackSettings) {
            return true;
        }
        return DataPackSettings.SAFE_MODE.getEnabled().equals(dataPackSettings.getEnabled()) && DataPackSettings.SAFE_MODE.getDisabled().equals(dataPackSettings.getDisabled());
    }

    public Set<String> getExpectedDataPacks() {
        Set<String> expectedDataPacks = new HashSet<>();
        expectedDataPacks.addAll(this.filterOnlyFileDataPacks(this.dataPackSettings.getEnabled()));
        expectedDataPacks.addAll(this.filterOnlyFileDataPacks(this.dataPackSettings.getDisabled()));
        return expectedDataPacks;
    }

    private Set<String> filterOnlyFileDataPacks(List<String> dataPacks) {
        Set<String> fileDataPacks = new HashSet<>(dataPacks);
        fileDataPacks.removeIf(dataPack -> !dataPack.startsWith("file/"));
        return fileDataPacks;
    }

    private JsonElement serializeDataPackSettings(DataPackSettings dataPackSettings) {
        JsonObject jsonObject = new JsonObject();
        JsonArray enabled = new JsonArray();
        for (String dataPack : dataPackSettings.getEnabled()) {
            enabled.add(dataPack);
        }
        jsonObject.add("enabled", enabled);
        JsonArray disabled = new JsonArray();
        for (String dataPack : dataPackSettings.getDisabled()) {
            disabled.add(dataPack);
        }
        jsonObject.add("disabled", disabled);
        return jsonObject;
    }

    private DataPackSettings deserializeDataPackSettings(JsonElement jsonElement) {
        List<String> enabled = this.deserializeDataPacks(jsonElement.getAsJsonObject().getAsJsonArray("enabled"));
        List<String> disabled = this.deserializeDataPacks(jsonElement.getAsJsonObject().getAsJsonArray("disabled"));
        return new DataPackSettings(enabled, disabled);
    }

    private List<String> deserializeDataPacks(JsonArray jsonArray) {
        List<String> dataPacks = new ArrayList<>();
        for (JsonElement dataPack : jsonArray) {
            String dataPackName = dataPack.getAsString();
            if (dataPacks.contains(dataPackName)) {
                continue;
            }
            dataPacks.add(dataPackName);
        }
        return dataPacks;
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
        Class<?> type = field.getType();
        if (GameRules.class.equals(type)) {
            return new SpeedrunConfigAPI.CustomOption.Builder<GameRules>(config, this, field, idPrefix)
                    .fromJson(((option, config_, configStorage, optionField, jsonElement) -> option.set(this.deserializeGameRules(jsonElement))))
                    .toJson(((option, config_, configStorage, optionField) -> this.serializeGameRules(option.get())))
                    .build();
        }
        if (DataPackSettings.class.equals(type)) {
            return new SpeedrunConfigAPI.CustomOption.Builder<DataPackSettings>(config, this, field, idPrefix)
                    .fromJson(((option, config_, configStorage, optionField, jsonElement) -> option.set(this.deserializeDataPackSettings(jsonElement))))
                    .toJson(((option, config_, configStorage, optionField) -> this.serializeDataPackSettings(option.get())))
                    .build();
        }
        return SpeedrunConfig.super.parseField(field, config, idPrefix);
    }

    public boolean updateHasLegalSettings() {
        return this.hasLegalSettings = (this.gameMode == CreateWorldScreen.Mode.SURVIVAL || this.gameMode == CreateWorldScreen.Mode.HARDCORE) &&
                this.structures &&
                !this.bonusChest &&
                !this.cheatsEnabled &&
                this.generatorType == AtumGeneratorType.DEFAULT &&
                !this.areGameRulesModified(this.gameRules) &&
                this.isDefaultDataPackSettings(this.dataPackSettings) &&
                !this.demoMode;
    }

    public Text getIllegalSettingsWarning() {
        List<Text> warnings = this.getIllegalSettingsTexts();
        if (warnings.isEmpty()) {
            return TextUtil.translatable("gui.none");
        }
        MutableText warning = warnings.remove(0).shallowCopy();
        for (Text w : warnings) {
            warning.append(", ").append(w);
        }
        return warning;
    }

    private List<Text> getIllegalSettingsTexts() {
        List<Text> texts = new ArrayList<>();
        if (this.gameMode != CreateWorldScreen.Mode.SURVIVAL && this.gameMode != CreateWorldScreen.Mode.HARDCORE) {
            texts.add(TextUtil.translatable("selectWorld.gameMode").append(": ").append(TextUtil.translatable("selectWorld.gameMode." + ((CreateWorldScreen$ModeAccessor) (Object) this.gameMode).atum$getTranslationSuffix())));
        }
        if (this.cheatsEnabled) {
            texts.add(TextUtil.translatable("selectWorld.allowCommands").append(" ").append(ScreenTexts.ON));
        }
        if (!this.structures) {
            texts.add(TextUtil.translatable("selectWorld.mapFeatures").append(" ").append(ScreenTexts.OFF));
        }
        if (this.bonusChest) {
            texts.add(TextUtil.translatable("selectWorld.bonusItems").append(" ").append(ScreenTexts.ON));
        }
        if (this.generatorType != AtumGeneratorType.DEFAULT) {
            texts.add(TextUtil.translatable("selectWorld.mapType").append(" ").append(this.generatorType.get().getDisplayName()));
        }
        if (this.modifiedGameRules) {
            texts.add(TextUtil.translatable("selectWorld.gameRules").append(": Modified"));
        }
        if (!this.isDefaultDataPackSettings(this.dataPackSettings)) {
            String dataPackInformation;
            if (this.dataPackMismatch) {
                dataPackInformation = "? | ?";
            } else {
                dataPackInformation = this.filterOnlyFileDataPacks(this.dataPackSettings.getEnabled()).size() + " | " + this.filterOnlyFileDataPacks(this.dataPackSettings.getDisabled()).size();
            }
            texts.add(TextUtil.translatable("selectWorld.dataPacks").append(": " + dataPackInformation));
        }
        if (this.demoMode) {
            texts.add(TextUtil.translatable("atum.config.demoMode", ScreenTexts.ON));
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
        this.setGameRules(new GameRules());
        if (Files.exists(this.dataPackDirectory)) {
            try {
                FileUtils.cleanDirectory(this.dataPackDirectory.toFile());
            } catch (IOException e) {
                Atum.LOGGER.error("Failed to clear datapack directory!", e);
            }
        }
        this.setDataPackSettings(DataPackSettings.SAFE_MODE);
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
            String creationSeed = ((ISeedStringHolder) server.getSaveProperties().getGeneratorOptions()).atum$getSeedString();
            if (!creationSeed.isEmpty()) {
                if (Atum.getSeedProvider().shouldShowSeed()) {
                    seedLine = "Resetting the seed \"" + creationSeed + "\"";
                } else {
                    seedLine = "Resetting a set seed";
                }
            } else {
                seedLine = "Resetting a random seed";
            }
            seedLine += ", ";
            if (Atum.config.gameMode == CreateWorldScreen.Mode.HARDCORE) {
                seedLine += "hc";
            } else {
                seedLine += Atum.config.difficulty.getName().charAt(0);
            }
            debugText.add(seedLine);
        }

        for (Text text : this.getIllegalSettingsTexts()) {
            debugText.add(text.getString());
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
    public @NotNull Screen createConfigScreen(Screen parent) {
        // isAvailable() already takes care of this, but because it's so important we do another check just to be completely sure Atum is not running when the player opens the Atum config
        if (Atum.isRunning()) {
            throw new IllegalStateException("Cannot configure Atum while it's running.");
        }
        return AtumCreateWorldScreen.create(parent, AtumCreateWorldScreen.Job.CONFIGURATION);
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
        SINGLE_BIOME_SURFACE(GeneratorTypeAccessor.atum$SINGLE_BIOME_SURFACE()),
        DEBUG(GeneratorTypeAccessor.atum$DEBUG_ALL_BLOCK_STATES());

        private final GeneratorType generatorType;

        AtumGeneratorType(GeneratorType generatorType) {
            this.generatorType = generatorType;
        }

        public GeneratorType get() {
            return this.generatorType;
        }

        public static @Nullable AtumGeneratorType from(GeneratorType generatorType) {
            for (AtumGeneratorType atumGeneratorType : values()) {
                if (atumGeneratorType.get() == generatorType) {
                    return atumGeneratorType;
                }
            }
            return null;
        }
    }
}
