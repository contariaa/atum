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
import me.contaria.speedrunapi.util.IdentifierUtil;
import me.contaria.speedrunapi.util.TextUtil;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import me.voidxwalker.autoreset.mixin.access.RuleAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.WorldPreset;
import net.minecraft.world.gen.WorldPresets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class AtumConfig implements SpeedrunConfig {
    @Config.Ignored
    private SpeedrunConfigContainer<?> container;

    public WorldCreator.Mode gameMode = WorldCreator.Mode.SURVIVAL;
    public boolean structures = true;
    public Difficulty difficulty = Difficulty.EASY;
    @Config.Strings.MaxChars(32)
    public String seed = "";
    public boolean bonusChest = false;
    public boolean cheatsEnabled;
    public AtumWorldType generatorType = AtumWorldType.DEFAULT;
    public String generatorDetails = "";
    @Config.Access(setter = "setGameRules")
    public GameRules gameRules = new GameRules(FeatureFlags.FEATURE_MANAGER.getFeatureSet());
    @Config.Access(setter = "setDataPackSettings")
    public DataPackSettings dataPackSettings = DataPackSettings.SAFE_MODE;
    public FeatureSet featureSet = FeatureFlags.DEFAULT_ENABLED_FEATURES;

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
        GameRules defaultGameRules = new GameRules(FeatureFlags.FEATURE_MANAGER.getFeatureSet());
        MutableBoolean modified = new MutableBoolean();
        gameRules.accept(new GameRules.Visitor() {
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
        GameRules defaultGameRules = new GameRules(FeatureFlags.FEATURE_MANAGER.getFeatureSet());
        JsonObject jsonObject = new JsonObject();
        gameRules.accept(new GameRules.Visitor() {
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
        GameRules gameRules = new GameRules(FeatureFlags.FEATURE_MANAGER.getFeatureSet());
        gameRules.accept(new GameRules.Visitor() {
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

    public JsonElement serializeFeatureSet(FeatureSet featureSet) {
        JsonObject jsonObject = new JsonObject();
        JsonArray enabled = new JsonArray();
        JsonArray disabled = new JsonArray();

        Set<Identifier> defaultFeatures = FeatureFlags.FEATURE_MANAGER.toId(FeatureFlags.DEFAULT_ENABLED_FEATURES);
        Set<Identifier> features = FeatureFlags.FEATURE_MANAGER.toId(featureSet);
        for (Identifier feature : FeatureFlags.FEATURE_MANAGER.toId(FeatureFlags.FEATURE_MANAGER.getFeatureSet())) {
            if (features.contains(feature)) {
                if (!defaultFeatures.contains(feature)) {
                    enabled.add(new JsonPrimitive(feature.toString()));
                }
            } else if (defaultFeatures.contains(feature)) {
                disabled.add(new JsonPrimitive(feature.toString()));
            }
        }
        if (!enabled.isEmpty()) {
            jsonObject.add("enabled", enabled);
        }
        if (!disabled.isEmpty()) {
            jsonObject.add("disabled", disabled);
        }

        return jsonObject;
    }

    public FeatureSet deserializeFeatureSet(JsonElement jsonElement) {
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        JsonArray enabled = jsonObject.getAsJsonArray("enabled");
        Set<Identifier> enabledFeatures = new HashSet<>();
        if (enabled != null) {
            for (JsonElement element : enabled) {
                enabledFeatures.add(IdentifierUtil.parse(element.getAsString()));
            }
        }

        JsonArray disabled = jsonObject.getAsJsonArray("disabled");
        Set<Identifier> disabledFeatures = new HashSet<>();
        if (disabled != null) {
            for (JsonElement element : disabled) {
                disabledFeatures.add(IdentifierUtil.parse(element.getAsString()));
            }
        }

        Set<Identifier> defaultFeatures = FeatureFlags.FEATURE_MANAGER.toId(FeatureFlags.DEFAULT_ENABLED_FEATURES);
        Set<Identifier> features = new HashSet<>();
        for (Identifier feature : FeatureFlags.FEATURE_MANAGER.toId(FeatureFlags.FEATURE_MANAGER.getFeatureSet())) {
            if (enabledFeatures.contains(feature) || (defaultFeatures.contains(feature) && !disabledFeatures.contains(feature))) {
                features.add(feature);
            }
        }

        return FeatureFlags.FEATURE_MANAGER.featureSetOf(features);
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
        if (FeatureSet.class.equals(type)) {
            return new SpeedrunConfigAPI.CustomOption.Builder<FeatureSet>(config, this, field, idPrefix)
                    .fromJson(((option, config_, configStorage, optionField, jsonElement) -> option.set(this.deserializeFeatureSet(jsonElement))))
                    .toJson(((option, config_, configStorage, optionField) -> this.serializeFeatureSet(option.get())))
                    .build();
        }
        return SpeedrunConfig.super.parseField(field, config, idPrefix);
    }

    public boolean updateHasLegalSettings() {
        return this.hasLegalSettings = (this.gameMode == WorldCreator.Mode.SURVIVAL || this.gameMode == WorldCreator.Mode.HARDCORE) &&
                this.structures &&
                !this.bonusChest &&
                !this.cheatsEnabled &&
                this.generatorType == AtumWorldType.DEFAULT &&
                !this.areGameRulesModified(this.gameRules) &&
                this.isDefaultDataPackSettings(this.dataPackSettings) &&
                !this.demoMode;
    }

    public Text getIllegalSettingsWarning() {
        List<Text> warnings = this.getIllegalSettingsTexts();
        if (warnings.isEmpty()) {
            return TextUtil.translatable("gui.none");
        }
        MutableText warning = warnings.remove(0).copyContentOnly();
        for (Text w : warnings) {
            warning.append(", ").append(w);
        }
        return warning;
    }

    private List<Text> getIllegalSettingsTexts() {
        List<Text> texts = new ArrayList<>();
        if (this.gameMode != WorldCreator.Mode.SURVIVAL && this.gameMode != WorldCreator.Mode.HARDCORE) {
            texts.add(TextUtil.translatable("selectWorld.gameMode").append(": ").append(this.gameMode.name));
        }
        if (this.cheatsEnabled) {
            texts.add(TextUtil.translatable("selectWorld.allowCommands").append(": ").append(ScreenTexts.ON));
        }
        if (!this.structures) {
            texts.add(TextUtil.translatable("selectWorld.mapFeatures").append(": ").append(ScreenTexts.OFF));
        }
        if (this.bonusChest) {
            texts.add(TextUtil.translatable("selectWorld.bonusItems").append(": ").append(ScreenTexts.ON));
        }
        if (this.generatorType != AtumWorldType.DEFAULT) {
            texts.add(TextUtil.translatable("selectWorld.mapType").append(": ").append(TextUtil.translatable(this.generatorType.worldPreset.getValue().toTranslationKey("generator"))));
        }
        if (this.modifiedGameRules) {
            texts.add(TextUtil.translatable("selectWorld.gameRules").append(": Modified"));
        }
        if (!this.isDefaultDataPackSettings(this.dataPackSettings)) {
            texts.add(TextUtil.translatable("selectWorld.dataPacks").append(": Modified"));
        }
        if (!this.featureSet.equals(FeatureFlags.DEFAULT_ENABLED_FEATURES)) {
            texts.add(TextUtil.translatable("selectWorld.experiments").append(": Modified"));
        }
        if (this.demoMode) {
            texts.add(TextUtil.translatable("atum.config.demoMode").append(": ").append(ScreenTexts.ON));
        }
        return texts;
    }

    public void resetToLegalSettings() {
        if (this.gameMode != WorldCreator.Mode.HARDCORE) {
            this.gameMode = WorldCreator.Mode.SURVIVAL;
        }
        this.structures = true;
        this.bonusChest = false;
        this.cheatsEnabled = false;
        this.generatorType = AtumWorldType.DEFAULT;
        this.generatorDetails = "";
        this.setGameRules(new GameRules(FeatureFlags.FEATURE_MANAGER.getFeatureSet()));
        if (Files.exists(this.dataPackDirectory)) {
            try {
                FileUtils.cleanDirectory(this.dataPackDirectory.toFile());
            } catch (IOException e) {
                Atum.LOGGER.error("Failed to clear datapack directory!", e);
            }
        }
        this.setDataPackSettings(DataPackSettings.SAFE_MODE);
        this.featureSet = FeatureFlags.DEFAULT_ENABLED_FEATURES;
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
            if (Atum.config.gameMode == WorldCreator.Mode.HARDCORE) {
                seedLine += "hc";
            } else {
                seedLine += Atum.config.difficulty.getName().charAt(0);
            }
            debugText.add(seedLine);
        }

        if (this.gameMode != WorldCreator.Mode.SURVIVAL && this.gameMode != WorldCreator.Mode.HARDCORE) {
            debugText.add("Game Mode: " + this.gameMode.name().substring(0, 1).toUpperCase(Locale.ROOT) + this.gameMode.name().substring(1).toLowerCase(Locale.ROOT));
        }
        if (this.cheatsEnabled) {
            debugText.add("Allow Commands: ON");
        }
        if (!this.structures) {
            debugText.add("Generate Structures: OFF");
        }
        if (this.bonusChest) {
            debugText.add("Bonus Chest: ON");
        }
        if (this.generatorType != AtumWorldType.DEFAULT) {
            String generatorInformation = this.generatorType.getName();
            if (!this.generatorDetails.isEmpty()) {
                generatorInformation += " (" + this.generatorDetails.hashCode() + ")";
            }
            debugText.add("World Type: " + generatorInformation);
        }
        if (this.modifiedGameRules) {
            debugText.add("Game Rules: Modified");
        }
        if (!this.isDefaultDataPackSettings(this.dataPackSettings)) {
            String dataPackInformation;
            if (this.dataPackMismatch) {
                dataPackInformation = "? | ?";
            } else {
                dataPackInformation = this.filterOnlyFileDataPacks(this.dataPackSettings.getEnabled()).size() + " | " + this.filterOnlyFileDataPacks(this.dataPackSettings.getDisabled()).size();
            }
            debugText.add("Data Packs: " + dataPackInformation);
        }
        if (!this.featureSet.equals(FeatureFlags.DEFAULT_ENABLED_FEATURES)) {
            debugText.add("Experiments: Modified");
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
    public enum AtumWorldType {
        DEFAULT(WorldPresets.DEFAULT, "Default"),
        FLAT(WorldPresets.FLAT, "Superflat"),
        LARGE_BIOMES(WorldPresets.LARGE_BIOMES, "Large Biomes"),
        AMPLIFIED(WorldPresets.AMPLIFIED, "AMPLIFIED"),
        SINGLE_BIOME_SURFACE(WorldPresets.SINGLE_BIOME_SURFACE, "Single Biome"),
        DEBUG(WorldPresets.DEBUG_ALL_BLOCK_STATES, "Debug Mode");

        private final RegistryKey<WorldPreset> worldPreset;
        private final String name;

        AtumWorldType(RegistryKey<WorldPreset> worldPreset, String name) {
            this.worldPreset = worldPreset;
            this.name = name;
        }

        public WorldCreator.WorldType get(Registry<WorldPreset> registry) {
            return new WorldCreator.WorldType(registry.getEntry(registry.get(this.worldPreset)));
        }

        public String getName() {
            return this.name;
        }

        public static @Nullable AtumWorldType from(WorldCreator.WorldType worldType) {
            for (AtumWorldType type : values()) {
                if (Objects.requireNonNull(worldType.preset()).matchesKey(type.worldPreset)) {
                    return type;
                }
            }
            return null;
        }
    }
}
