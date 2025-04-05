package me.voidxwalker.autoreset.api.seedprovider;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface SeedProvider {
    /**
     * Requests a seed from the seed provider. Can be completed immediately in the implemented method to skip any
     * waiting screens.
     */
    CompletableFuture<String> requestSeed();

    /**
     * Determines whether a set seed should be present in logs, LevelLoadingScreen, and DebugHUD.
     */
    default boolean shouldShowSeed() {
        return true;
    }

    /**
     * Gets the waiting screen.
     * The implemented waiting screen should run the provided continueWorldCreation method once a seed is available, or alternatively cancelWorldCreation.
     * The implemented waiting screen can also override shouldCloseOnEsc(), returning true to allow cancelling with the 'escape' key.
     */
    default Optional<AtumWaitingScreen> getWaitingScreen() {
        return Optional.empty();
    }

    /**
     * Runs when an exception or cancellation occurs while resolving the seed future.
     */
    default void onFail(@Nullable Throwable ex){
    }
}
