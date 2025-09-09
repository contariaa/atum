package me.voidxwalker.autoreset.api.seedprovider;

import net.minecraft.client.gui.screen.Screen;

import java.util.LinkedList;
import java.util.List;

/**
 * A waiting screen intended to wait for a seed to become playable with the ability to cancel playing a seed.
 */
public abstract class AtumWaitingScreen extends Screen {
    private final List<Runnable> onTick = new LinkedList<>();
    private final List<Runnable> onCancel = new LinkedList<>();

    protected AtumWaitingScreen() {
    }

    @SuppressWarnings("unused")
    protected final void cancelWorldCreation() {
        this.onClose();
    }

    @Override
    protected void keyPressed(char id, int code) {
        // do not close on esc
    }

    public final void onClose() {
        for (Runnable r : this.onCancel) {
            r.run();
        }
    }

    @Override
    public final void tick() {
        for (Runnable r : this.onTick) {
            r.run();
        }
    }

    public final void addTickActivity(Runnable r) {
        this.onTick.add(r);
    }

    public final void addCancelActivity(Runnable r) {
        this.onCancel.add(r);
    }
}