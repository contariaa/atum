package me.voidxwalker.autoreset.mixin.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.AtumConfig;
import me.voidxwalker.autoreset.interfaces.IMoreOptionsDialog;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import me.voidxwalker.autoreset.mixin.access.GeneratorTypeAccessor;
import net.minecraft.client.gui.screen.world.MoreOptionsDialog;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.world.GeneratorType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(MoreOptionsDialog.class)
public abstract class MoreOptionsDialogMixin implements IMoreOptionsDialog {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Shadow
    private Optional<GeneratorType> generatorType;
    @Shadow
    private GeneratorOptions generatorOptions;
    @Shadow
    private RegistryTracker.Modifiable registryManager;
    @Shadow
    private String seedText;
    @Shadow
    private TextFieldWidget seedTextField;

    @Override
    public void atum$loadAtumConfigurations() {
        if (Atum.config.generatorType == AtumConfig.AtumGeneratorType.DEFAULT) {
            if (Atum.config.structures != this.generatorOptions.shouldGenerateStructures()) {
                this.generatorOptions = this.generatorOptions.toggleGenerateStructures();
            }
            if (Atum.config.bonusChest != this.generatorOptions.hasBonusChest()) {
                this.generatorOptions = this.generatorOptions.toggleBonusChest();
            }
            return;
        }

        GeneratorType generatorType = Atum.config.generatorType.get();
        this.generatorType = Optional.of(generatorType);
        this.generatorOptions = generatorType.createDefaultOptions(this.registryManager, this.generatorOptions.getSeed(), Atum.config.structures, Atum.config.bonusChest);

        if (Atum.config.generatorDetails.isEmpty()) {
            return;
        }

        switch (Atum.config.generatorType) {
            case FLAT:
                FlatChunkGeneratorConfig.CODEC.parse(
                        JsonOps.INSTANCE,
                        new JsonParser().parse(Atum.config.generatorDetails)
                ).resultOrPartial(
                        error -> Atum.LOGGER.warn("Failed to deserialize flat world generator details!")
                ).ifPresent(generatorConfig -> this.generatorOptions = this.generatorOptions.withDimensions(
                        GeneratorOptions.getRegistryWithReplacedOverworldGenerator(
                                this.generatorOptions.getDimensionMap(),
                                new FlatChunkGenerator(generatorConfig)
                        )
                ));
                break;
            case SINGLE_BIOME_SURFACE:
            case SINGLE_BIOME_CAVES:
            case SINGLE_BIOME_FLOATING_ISLANDS:
                Identifier biomeID = new Identifier(Atum.config.generatorDetails);
                Optional<Biome> biome = Registry.BIOME.getOrEmpty(new Identifier(Atum.config.generatorDetails));
                if (biome.isPresent()) {
                    this.generatorOptions = GeneratorTypeAccessor.atum$createFixedBiomeOptions(this.generatorOptions, Atum.config.generatorType.get(), biome.get());
                } else {
                    Atum.LOGGER.warn("Failed to parse biome: {}", biomeID);
                }
        }
    }

    @Override
    public void atum$setSeed(String seedString) {
        this.seedText = seedString;
        this.seedTextField.setText(this.seedText);
        if (Atum.isRunning()) {
            ((ISeedStringHolder) this.generatorOptions).atum$setSeedString(seedString);
        }
    }

    @Override
    public void atum$saveAtumConfigurations() {
        Atum.config.seed = this.seedText;
        Atum.config.generatorType = this.generatorType.map(AtumConfig.AtumGeneratorType::from).orElse(AtumConfig.AtumGeneratorType.DEFAULT);
        Atum.config.structures = this.generatorOptions.shouldGenerateStructures();
        Atum.config.bonusChest = this.generatorOptions.hasBonusChest();

        String generatorDetails = "";
        switch (Atum.config.generatorType) {
            case FLAT:
                generatorDetails = FlatChunkGeneratorConfig.CODEC.encode(
                        ((FlatChunkGenerator) this.generatorOptions.getChunkGenerator()).getGeneratorConfig(),
                        JsonOps.INSTANCE,
                        new JsonObject()
                ).resultOrPartial(
                        error -> Atum.LOGGER.warn("Failed to serialize flat world generator details!")
                ).map(JsonElement::toString).orElse("");
                break;
            case SINGLE_BIOME_SURFACE:
            case SINGLE_BIOME_CAVES:
            case SINGLE_BIOME_FLOATING_ISLANDS:
                generatorDetails = Registry.BIOME.getKey(this.generatorOptions.getChunkGenerator().getBiomeSource().getBiomes().get(0))
                        .map(RegistryKey::getValue)
                        .map(Identifier::toString)
                        .orElse("");
        }
        Atum.config.generatorDetails = generatorDetails;
    }

    @Override
    public boolean atum$isSetSeed() {
        return !this.seedText.isEmpty();
    }
}
