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
     * Gets the waiting screen. The implemented screen only needs to override the render method. It may also allow for
     * cancelling of seeds with esc, or it can call cancelWorldCreation through other means such as a button.
     */
    default Optional<AtumWaitingScreen> getWaitingScreen() {
        return Optional.empty();
    }

    /**
     * Runs when an exception or cancellation occurs while resolving the seed future.
     * This method will be called individually for each exception with no guarantee of timing.
     * This method is guaranteed to be called from the client thread.
     */
    @SuppressWarnings("unused")
    default void onFail(@Nullable Throwable ex) {
    }
}