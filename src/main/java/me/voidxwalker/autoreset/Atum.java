package me.voidxwalker.autoreset;

import io.netty.util.internal.ConcurrentSet;
import me.voidxwalker.autoreset.api.seedprovider.AtumWaitingScreen;
import me.voidxwalker.autoreset.api.seedprovider.SeedProvider;
import me.voidxwalker.autoreset.mixin.access.CreativeInventoryScreenAccessor;
import me.voidxwalker.autoreset.mixin.access.RecipeBookWidgetAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.gui.screen.options.ControlsOptionsScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.options.KeyBinding;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Atum implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger();

    public static final boolean HAS_WORLDPREVIEW = FabricLoader.getInstance().isModLoaded("worldpreview");

    public static AtumConfig config;
    public static KeyBinding resetKey;

    private static boolean running = false;
    private static boolean shouldReset;

    public static final Queue<Throwable> SEED_FAILURES = new ConcurrentLinkedQueue<>();
    public static final Set<CompletableFuture<String>> SEED_FUTURES = new ConcurrentSet<>();
    private static final SeedProvider DEFAULT_SEED_PROVIDER = () -> CompletableFuture.completedFuture(Atum.config.seed);
    private static SeedProvider seedProvider = DEFAULT_SEED_PROVIDER;

    public static void createNewWorld() {
        running = true;
        shouldReset = false;

        MinecraftClient.getInstance().openScreen(new AtumCreateWorldScreen(null));
    }

    public static boolean isRunning() {
        return running;
    }

    public static void stopRunning() {
        shouldReset = false;
        running = false;
        config.dataPackMismatch = false;
        cancelAllSeeds();
    }

    public static void scheduleReset() {
        if (!(MinecraftClient.getInstance().currentScreen instanceof AtumWaitingScreen)) {
            shouldReset = true;
        }
    }

    public static boolean isResetScheduled() {
        return shouldReset;
    }

    public static boolean shouldReset() {
        return isResetScheduled() && !isBlocking();
    }

    public static boolean isBlocking() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getOverlay() != null || isLoadingWorld() || client.currentScreen instanceof AtumWaitingScreen;
    }

    public static boolean isInWorld() {
        return MinecraftClient.getInstance().world != null;
    }

    public static boolean isLoadingWorld() {
        return MinecraftClient.getInstance().getServer() != null && MinecraftClient.getInstance().world == null;
    }

    public static boolean inDemoMode() {
        return isRunning() && config.demoMode;
    }

    /**
     * Returns true if the seed is set by Atum and no external seed provider is used, used by chunkcacher.
     */
    @SuppressWarnings("unused")
    public static boolean isSetSeed() {
        return Atum.seedProvider == DEFAULT_SEED_PROVIDER && (config.isSetSeed() || config.demoMode);
    }

    public static SeedProvider getSeedProvider() {
        return seedProvider;
    }

    @SuppressWarnings("unused")
    public static void setSeedProvider(SeedProvider seedProvider) {
        Atum.ensureState(Atum.seedProvider == DEFAULT_SEED_PROVIDER, "Seed provider has already been changed! It is likely that multiple mods are trying to set the seed provider!");
        Atum.ensureState(!Atum.isRunning(), "Seed provider set at an illegal time!");
        Atum.seedProvider = Objects.requireNonNull(seedProvider);
    }

    public static void ensureState(boolean condition, String exceptionMessage) throws IllegalStateException {
        if (!condition) throw new IllegalStateException(exceptionMessage);
    }

    public static void cancelAllSeeds() {
        // Copy the collection to avoid modification during iteration
        new ArrayList<>(SEED_FUTURES).forEach(f -> f.cancel(true));
    }

    public static void checkSeedFailures() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!SEED_FAILURES.isEmpty()) {
            if (isRunning()) {
                stopRunning();
                if (client.world == null) {
                    client.openScreen(null);
                }
            }
            while (!SEED_FAILURES.isEmpty()) {
                getSeedProvider().onFail(SEED_FAILURES.poll());
            }
        }
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "RedundantIfStatement"})
    public static boolean canReset(MinecraftClient client) {
        Screen curr = client.currentScreen;
        if (curr == null) {
            return true;
        }
        if (curr instanceof ControlsOptionsScreen && ((ControlsOptionsScreen) curr).focusedBinding == Atum.resetKey) {
            return false;
        }
        if (!Atum.config.safeHotkey) {
            return true;
        }
        Element focused = curr.getFocused();
        if (focused instanceof TextFieldWidget && ((TextFieldWidget) focused).isActive()) {
            return false;
        }
        if (focused instanceof RecipeBookWidget && ((RecipeBookWidgetAccessor) focused).atum$getSearchField() != null && ((RecipeBookWidgetAccessor) focused).atum$getSearchField().isFocused()) {
            return false;
        }
        if (curr instanceof CreativeInventoryScreen && ((CreativeInventoryScreenAccessor) curr).atum$getSearchBox().isActive()) {
            return false;
        }
        if (curr instanceof SignEditScreen) {
            return false;
        }
        // missing gamerules screen and speedrunapi config screen support
        return true;
    }

    @Override
    public void onInitializeClient() {
        resetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Create New World",
                GLFW.GLFW_KEY_F6,
                "key.categories.atum"
        ));
    }
}
