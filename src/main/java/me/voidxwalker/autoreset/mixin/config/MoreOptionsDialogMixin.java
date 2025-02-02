package me.voidxwalker.autoreset.mixin.config;

import com.google.gson.JsonElement;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.registry.*;
import net.minecraft.world.biome.Biome;
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
    private DynamicRegistryManager.Immutable registryManager;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Shadow
    private OptionalLong seed;
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
                        // TODO: This always fails with "Not a registry ops"
                        //       RegistryOps.of(JsonOps.INSTANCE, ?, this.registryManager)
                        JsonOps.INSTANCE,
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
                                new FlatChunkGenerator(this.registryManager.get(Registry.STRUCTURE_SET_KEY), generatorConfig)
                        )
                ));
                break;
            case SINGLE_BIOME_SURFACE:
                Identifier id = IdentifierUtil.parse(Atum.config.generatorDetails);
                Optional<RegistryEntry<Biome>> biome = BuiltinRegistries.BIOME.getOrEmpty(id).flatMap(BuiltinRegistries.BIOME::getKey).map(BuiltinRegistries.BIOME::entryOf);
                if (biome.isPresent()) {
                    this.generatorOptions = GeneratorTypeAccessor.atum$createFixedBiomeOptions(this.registryManager, this.generatorOptions, biome.get());
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
            case FLAT ->
                    // TODO: This now also fails with "Can't access registry"
                    FlatChunkGeneratorConfig.CODEC.encode(
                            ((FlatChunkGenerator) this.generatorOptions.getChunkGenerator()).getConfig(),
                            JsonOps.INSTANCE,
                            new JsonObject()
                    ).resultOrPartial(
                            error -> Atum.LOGGER.warn("Failed to serialize flat world generator details! {}", error)
                    ).map(JsonElement::toString).orElse("");
            case SINGLE_BIOME_SURFACE ->
                    this.generatorOptions.getChunkGenerator().getBiomeSource().getBiomes().iterator().next().getKey()
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
