package me.voidxwalker.autoreset.mixin.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.serialization.JsonOps;
import me.contaria.speedrunapi.util.IdentifierUtil;
import me.contaria.speedrunapi.util.TextUtil;
import me.voidxwalker.autoreset.AttemptTracker;
import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.AtumConfig;
import me.voidxwalker.autoreset.AtumCreateWorldScreen;
import me.voidxwalker.autoreset.AtumCreateWorldScreen.Job;
import me.voidxwalker.autoreset.api.seedprovider.AtumWaitingScreen;
import me.voidxwalker.autoreset.api.seedprovider.SeedProvider;
import me.voidxwalker.autoreset.interfaces.ISeedStringHolder;
import me.voidxwalker.autoreset.mixin.access.LevelScreenProviderAccessor;
import me.voidxwalker.autoreset.mixin.access.LevelScreenProviderAccessor2;
import me.voidxwalker.autoreset.mixin.access.WorldCreatorAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
    @Shadow
    @Final
    private Screen parent;

    @Shadow
    @Final
    WorldCreator worldCreator;

    @Shadow
    @Nullable
    private Path dataPackTempDir;

    @Unique
    private CompletableFuture<String> seedFuture;

    @Shadow
    protected abstract void createLevel();

    @Shadow
    protected abstract <T extends Element & Drawable & Selectable> T addDrawableChild(T drawableElement);

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

    @ModifyArg(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/ButtonWidget;builder(Lnet/minecraft/text/Text;Lnet/minecraft/client/gui/widget/ButtonWidget$PressAction;)Lnet/minecraft/client/gui/widget/ButtonWidget$Builder;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=selectWorld.create"
                    )
            ),
            index = 0
    )
    private Text replaceCreateNewWorldMessage(Text message) {
        if (this.isAtum()) {
            return ScreenTexts.DONE;
        }
        return message;
    }

    @WrapWithCondition(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/widget/DirectionalLayoutWidget;add(Lnet/minecraft/client/gui/widget/Widget;)Lnet/minecraft/client/gui/widget/Widget;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/screen/ScreenTexts;CANCEL:Lnet/minecraft/text/Text;"
                    )
            )
    )
    private boolean removeCancelButton(DirectionalLayoutWidget instance, Widget widget) {
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

        this.initDataPacks();
        if (this.isAtumReset()) {
            this.continueReset();
            return;
        }

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

    @ModifyExpressionValue(
            method = "copyDataPack(Ljava/nio/file/Path;Lnet/minecraft/client/MinecraftClient;)Ljava/nio/file/Path;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"
            )
    )
    private static Stream<Path> filterAtumDataPacks(Stream<Path> dataPacks, Path directory, MinecraftClient client) {
        if (!directory.equals(Atum.config.dataPackDirectory)) {
            return dataPacks;
        }

        Set<String> expected = Atum.config.getExpectedDataPacks();
        Set<Path> paths = new HashSet<>();

        // instantly collect to bypass lazy evaluation
        dataPacks = dataPacks.filter(path -> {
            // check if datapacks are expected in main directory
            if (path.getParent().equals(directory)) {
                String name = "file/" + Atum.config.dataPackDirectory.relativize(path);
                if (expected.remove(name)) {
                    paths.add(path);
                    return true;
                }
                return false;
            }
            // check if path belongs to any expected datapack
            for (Path dataPack : paths) {
                if (path.startsWith(dataPack)) {
                    return true;
                }
            }
            return false;
        }).toList().stream();

        if (!expected.isEmpty()) {
            Atum.config.dataPackMismatch = true;
            Atum.LOGGER.warn("Data pack mismatch, some of the configured files are missing!");
        }
        return dataPacks;
    }

    @Unique
    private void load() {
        this.worldCreator.setSeed(Atum.config.seed);
        this.worldCreator.setGameMode(Atum.config.gameMode);
        this.worldCreator.setDifficulty(Atum.config.difficulty);
        this.worldCreator.setCheatsEnabled(Atum.config.cheatsEnabled);
        this.worldCreator.setGenerateStructures(Atum.config.structures);
        this.worldCreator.setBonusChestEnabled(Atum.config.bonusChest);

        this.worldCreator.setWorldType(Atum.config.generatorType.get(this.worldCreator.getGeneratorOptionsHolder().getCombinedRegistryManager().get(RegistryKeys.WORLD_PRESET)));
        this.loadGeneratorDetails(Atum.config.generatorType, Atum.config.generatorDetails);

        if (Atum.config.hasModifiedGameRules()) {
            this.worldCreator.getGameRules().setAllValues(Atum.config.gameRules, null);
        }
        this.worldCreator.setGeneratorOptionsHolder(new GeneratorOptionsHolder(
                this.worldCreator.getGeneratorOptionsHolder().generatorOptions(),
                this.worldCreator.getGeneratorOptionsHolder().dimensionOptionsRegistry(),
                this.worldCreator.getGeneratorOptionsHolder().selectedDimensions(),
                this.worldCreator.getGeneratorOptionsHolder().combinedDynamicRegistries(),
                this.worldCreator.getGeneratorOptionsHolder().dataPackContents(),
                new DataConfiguration(Atum.config.dataPackSettings, Atum.config.featureSet)
        ));
    }

    @Unique
    private void loadGeneratorDetails(AtumConfig.AtumWorldType type, String generatorDetails) {
        if (type == null || generatorDetails.isEmpty()) {
            return;
        }
        switch (type) {
            case FLAT -> {
                FlatChunkGeneratorConfig.CODEC.parse(
                        RegistryOps.of(JsonOps.INSTANCE, this.worldCreator.getGeneratorOptionsHolder().getCombinedRegistryManager()),
                        JsonHelper.deserialize(generatorDetails)
                ).resultOrPartial(
                        error -> Atum.LOGGER.warn("Failed to deserialize flat world generator details! {}", error)
                ).ifPresent(generatorConfig -> this.worldCreator.applyModifier(LevelScreenProviderAccessor.atum$createFlatModifier(generatorConfig)));
            }
            case SINGLE_BIOME_SURFACE -> {
                Registry<Biome> registry = this.worldCreator.getGeneratorOptionsHolder().getCombinedRegistryManager().get(RegistryKeys.BIOME);
                Identifier id = IdentifierUtil.parse(generatorDetails);
                Optional<RegistryEntry<Biome>> biome = registry.getOrEmpty(id).flatMap(registry::getKey).map(registry::entryOf);
                if (biome.isPresent()) {
                    this.worldCreator.applyModifier(LevelScreenProviderAccessor2.atum$createSingleBiomeModifier(biome.get()));
                } else {
                    Atum.LOGGER.warn("Failed to parse biome: {}", id);
                }
            }
        }
    }

    @Unique
    private void initDataPacks() {
        if (this.isAtumConfig()) {
            this.dataPackTempDir = Atum.config.dataPackDirectory;
            return;
        }

        if (Atum.config.isDefaultDataPackSettings(this.worldCreator.getGeneratorOptionsHolder().dataConfiguration().dataPacks())) {
            return;
        }

        if (!Files.isDirectory(Atum.config.dataPackDirectory)) {
            Atum.config.dataPackMismatch = true;
            Atum.LOGGER.warn("Data pack mismatch, the Atum data pack directory is missing!");
            return;
        }

        this.dataPackTempDir = CreateWorldScreen.copyDataPack(Atum.config.dataPackDirectory, this.client);
        if (this.dataPackTempDir == null) {
            Atum.config.dataPackMismatch = true;
            Atum.LOGGER.warn("Data pack mismatch, failed to copy data packs!");
        }
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
                    // If the seed future is done, rerun init() to either continue to world generation or cancel
                    if (this.seedFuture.isDone()) {
                        this.continueReset();
                    }
                });
                MinecraftClient.getInstance().setScreen(waitingScreen);
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
            MinecraftClient.getInstance().createIntegratedServerLoader().createAndStart(demoWorldName, MinecraftServer.DEMO_LEVEL_INFO, GeneratorOptions.DEMO_OPTIONS, WorldPresets::createDemoOptions, null);
            return;
        }

        this.worldCreator.setSeed(seed);
        ((ISeedStringHolder) this.worldCreator.getGeneratorOptionsHolder().generatorOptions()).atum$setSeedString(seed);

        if (!seed.isEmpty() && Atum.getSeedProvider().shouldShowSeed()) {
            Atum.LOGGER.info("Creating \"{}\" with seed \"{}\"...", this.worldCreator.getWorldName(), seed);
        } else {
            Atum.LOGGER.info("Creating \"{}\"...", this.worldCreator.getWorldName());
        }
        this.createLevel();
    }

    @Unique
    private void initConfigScreen() {
        this.worldCreator.setWorldName(Atum.config.attemptTracker.getWorldName(
                this.worldCreator.getSeed().isEmpty() ? AttemptTracker.Type.SSG : AttemptTracker.Type.RSG
        ));
        this.worldCreator.addListener(
                worldCreator -> {
                    String worldName = Atum.config.attemptTracker.getWorldName(
                            worldCreator.getSeed().isEmpty() ? AttemptTracker.Type.SSG : AttemptTracker.Type.RSG
                    );
                    if (!worldName.equals(worldCreator.getWorldName())) {
                        worldCreator.setWorldName(worldName);
                    }
                }
        );
    }

    @Unique
    private void save() {
        Atum.config.gameMode = ((WorldCreatorAccessor) this.worldCreator).atum$getGameMode();
        Atum.config.difficulty = ((WorldCreatorAccessor) this.worldCreator).atum$getDifficulty();
        Atum.config.cheatsEnabled = ((WorldCreatorAccessor) this.worldCreator).atum$getCheatsEnabled();
        Atum.config.structures = ((WorldCreatorAccessor) this.worldCreator).atum$shouldGenerateStructures();
        Atum.config.bonusChest = ((WorldCreatorAccessor) this.worldCreator).atum$isBonusChestEnabled();

        Atum.config.generatorType = AtumConfig.AtumWorldType.from(this.worldCreator.getWorldType());
        Atum.config.generatorDetails = this.saveGeneratorDetails(Atum.config.generatorType);

        Atum.config.setGameRules(this.worldCreator.getGameRules().copy());
        Atum.config.setDataPackSettings(this.worldCreator.getGeneratorOptionsHolder().dataConfiguration().dataPacks());
        Atum.config.featureSet = this.worldCreator.getGeneratorOptionsHolder().dataConfiguration().enabledFeatures();
    }

    @Unique
    private String saveGeneratorDetails(AtumConfig.AtumWorldType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case FLAT -> FlatChunkGeneratorConfig.CODEC.encode(
                    ((FlatChunkGenerator) this.worldCreator.getGeneratorOptionsHolder().selectedDimensions().getChunkGenerator()).getConfig(),
                    RegistryOps.of(JsonOps.INSTANCE, this.worldCreator.getGeneratorOptionsHolder().getCombinedRegistryManager()),
                    new JsonObject()
            ).resultOrPartial(
                    error -> Atum.LOGGER.warn("Failed to serialize flat world generator details! {}", error)
            ).map(JsonElement::toString).orElse("");
            case SINGLE_BIOME_SURFACE ->
                    this.worldCreator.getGeneratorOptionsHolder().selectedDimensions().getChunkGenerator().getBiomeSource().getBiomes().iterator().next().getKey()
                            .map(RegistryKey::getValue)
                            .map(Identifier::toString)
                            .orElse("");
            default -> "";
        };
    }

    @Unique
    private void closeConfigScreen() {
        if (Atum.config.updateHasLegalSettings()) {
            Atum.config.save();
            MinecraftClient.getInstance().setScreen(this.parent);
            return;
        }
        MinecraftClient.getInstance().setScreen(new ConfirmScreen(confirm -> {
            if (!confirm) {
                Atum.config.resetToLegalSettings();
            }
            Atum.config.save();
            MinecraftClient.getInstance().setScreen(this.parent);
        }, TextUtil.translatable("atum.menu.legal_settings.warning"), Atum.config.getIllegalSettingsWarning(), TextUtil.translatable("atum.menu.legal_settings.confirm"), TextUtil.translatable("atum.menu.legal_settings.reset")));
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

    @Mixin(targets = "net/minecraft/client/gui/screen/world/CreateWorldScreen$GameTab")
    public static class GameTabMixin {
        @Shadow
        @Final
        private TextFieldWidget worldNameField;

        @WrapWithCondition(
                method = "<init>",
                at = @At(
                        value = "INVOKE",
                        target = "Lnet/minecraft/client/gui/screen/world/WorldCreator;addListener(Ljava/util/function/Consumer;)V",
                        ordinal = 0
                )
        )
        private boolean deactivateWorldNameFieldTooltip(WorldCreator worldCreator, Consumer<WorldCreator> listener, CreateWorldScreen createWorldScreen) {
            return !(createWorldScreen instanceof AtumCreateWorldScreen);
        }

        @WrapWithCondition(
                method = "<init>",
                at = @At(
                        value = "INVOKE",
                        target = "Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;method_48649(Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;Lnet/minecraft/client/gui/Element;)V"
                )
        )
        private boolean deactivateWorldNameFieldInitialFocus(CreateWorldScreen createWorldScreen, Element element) {
            return !(createWorldScreen instanceof AtumCreateWorldScreen);
        }

        @ModifyVariable(
                method = "<init>",
                at = @At(
                        value = "INVOKE",
                        target = "Lnet/minecraft/client/gui/widget/GridWidget$Adder;add(Lnet/minecraft/client/gui/widget/Widget;)Lnet/minecraft/client/gui/widget/Widget;",
                        shift = At.Shift.AFTER
                ),
                ordinal = 0
        )
        private GridWidget.Adder addDemoModeButton(GridWidget.Adder adder, CreateWorldScreen createWorldScreen) {
            if (createWorldScreen instanceof AtumCreateWorldScreen) {
                adder.add(CyclingButtonWidget.onOffBuilder().build(0, 0, 210, 20, TextUtil.translatable("atum.config.demoMode"), (button, value) -> Atum.config.demoMode = value));
            }
            return adder;
        }

        @Inject(
                method = "<init>",
                at = @At("TAIL")
        )
        private void deactivateWorldNameField(CreateWorldScreen createWorldScreen, CallbackInfo ci) {
            if (createWorldScreen instanceof AtumCreateWorldScreen) {
                this.worldNameField.setEditable(false);
                this.worldNameField.setFocusUnlocked(false);
                this.worldNameField.active = false;
            }
        }
    }
}
