package me.voidxwalker.autoreset.mixin.config;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import me.contaria.speedrunapi.util.IdentifierUtil;
import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.AtumConfig;
import me.voidxwalker.autoreset.interfaces.IMoreOptionsDialog;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import me.voidxwalker.autoreset.mixin.access.GeneratorTypeAccessor;
import net.minecraft.client.gui.screen.world.MoreOptionsDialog;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.world.GeneratorType;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.dynamic.RegistryOps;
import net.minecraft.util.registry.BuiltinRegistries;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;
import java.util.OptionalLong;

@Mixin(MoreOptionsDialog.class)
public abstract class MoreOptionsDialogMixin implements IMoreOptionsDialog {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Shadow
    private Optional<GeneratorType> generatorType;
    @Shadow
    private GeneratorOptions generatorOptions;
    @Shadow
    private DynamicRegistryManager.Impl registryManager;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Shadow
    private OptionalLong seed;
    @Shadow
    private TextFieldWidget seedTextField;

    @Override
    public void atum$loadAtumConfigurations() {
        GeneratorType generatorType = Atum.config.generatorType.get();
        this.generatorType = Optional.of(generatorType);

        if (Atum.config.generatorType == AtumConfig.AtumGeneratorType.DEFAULT) {
            if (Atum.config.structures != this.generatorOptions.shouldGenerateStructures()) {
                this.generatorOptions = this.generatorOptions.toggleGenerateStructures();
            }
            if (Atum.config.bonusChest != this.generatorOptions.hasBonusChest()) {
                this.generatorOptions = this.generatorOptions.toggleBonusChest();
            }
            return;
        }

        this.generatorOptions = generatorType.createDefaultOptions(this.registryManager, this.generatorOptions.getSeed(), Atum.config.structures, Atum.config.bonusChest);

        if (Atum.config.generatorDetails.isEmpty()) {
            return;
        }

        switch (Atum.config.generatorType) {
            case FLAT:
                FlatChunkGeneratorConfig.CODEC.parse(
                        RegistryOps.ofLoaded(JsonOps.INSTANCE, ResourceManager.Empty.INSTANCE, this.registryManager),
                        JsonHelper.deserialize(Atum.config.generatorDetails)
                ).resultOrPartial(
                        error -> Atum.LOGGER.warn("Failed to deserialize flat world generator details! {}", error)
                ).ifPresent(generatorConfig -> this.generatorOptions = new GeneratorOptions(
                        this.generatorOptions.getSeed(),
                        this.generatorOptions.shouldGenerateStructures(),
                        this.generatorOptions.hasBonusChest(),
                        GeneratorOptions.getRegistryWithReplacedOverworldGenerator(
                                this.registryManager.get(Registry.DIMENSION_TYPE_KEY),
                                this.generatorOptions.getDimensions(),
                                new FlatChunkGenerator(generatorConfig)
                        )
                ));
                break;
            case SINGLE_BIOME_SURFACE:
            case SINGLE_BIOME_CAVES:
            case SINGLE_BIOME_FLOATING_ISLANDS:
                Identifier id = IdentifierUtil.parse(Atum.config.generatorDetails);
                Optional<Biome> biome = BuiltinRegistries.BIOME.getOrEmpty(id);
                if (biome.isPresent()) {
                    this.generatorOptions = GeneratorTypeAccessor.atum$createFixedBiomeOptions(this.registryManager, this.generatorOptions, Atum.config.generatorType.get(), biome.get());
                } else {
                    Atum.LOGGER.warn("Failed to parse biome: {}", id);
                }
        }
    }

    @Override
    public void atum$setSeed(String seedString) {
        this.seedTextField.setText(seedString);
        if (Atum.isRunning()) {
            ((ISeedStringHolder) this.generatorOptions).atum$setSeedString(seedString);
        }
    }

    @Override
    public void atum$saveAtumConfigurations() {
        Atum.config.seed = this.seedTextField.getText();
        Atum.config.generatorType = this.generatorType.map(AtumConfig.AtumGeneratorType::from).orElse(AtumConfig.AtumGeneratorType.DEFAULT);
        Atum.config.structures = this.generatorOptions.shouldGenerateStructures();
        Atum.config.bonusChest = this.generatorOptions.hasBonusChest();

        Atum.config.generatorDetails = switch (Atum.config.generatorType) {
            case FLAT -> FlatChunkGeneratorConfig.CODEC.encode(
                    ((FlatChunkGenerator) this.generatorOptions.getChunkGenerator()).getConfig(),
                    RegistryOps.ofLoaded(JsonOps.INSTANCE, ResourceManager.Empty.INSTANCE, this.registryManager),
                    new JsonObject()
            ).resultOrPartial(
                    error -> Atum.LOGGER.warn("Failed to serialize flat world generator details! {}", error)
            ).map(e -> {
                // biome serializes as a bunch of fun facts about the biome instead of the biome's id, so we have to fix that
                JsonObject settings = e.getAsJsonObject();
                settings.remove("biome");
                Biome biome = ((FlatChunkGenerator) this.generatorOptions.getChunkGenerator()).getConfig().getBiome();
                settings.addProperty("biome", registryManager.get(Registry.BIOME_KEY).getKey(biome).orElse(BiomeKeys.PLAINS).getValue().toString());
                return e.toString();
            }).orElse("");
            case SINGLE_BIOME_SURFACE, SINGLE_BIOME_CAVES, SINGLE_BIOME_FLOATING_ISLANDS ->
                    BuiltinRegistries.BIOME.getKey(this.generatorOptions.getChunkGenerator().getBiomeSource().getBiomes().get(0))
                            .map(RegistryKey::getValue)
                            .map(Identifier::toString)
                            .orElse("");
            default -> "";
        };
    }

    @Override
    public boolean atum$isSetSeed() {
        return this.seed.isPresent();
    }
}
