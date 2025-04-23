package me.voidxwalker.autoreset.api.seedprovider;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.LinkedList;
import java.util.List;

/**
 * A waiting screen intended to wait for a seed to become playable with the ability to cancel playing a seed.
 */
public abstract class AtumWaitingScreen extends Screen {
    private final List<Runnable> onTick = new LinkedList<>();
    private final List<Runnable> onCancel = new LinkedList<>();

    protected AtumWaitingScreen(Text title) {
        super(title);
    }

    @SuppressWarnings("unused")
    protected final void cancelWorldCreation() {
        this.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public final void onClose() {
        for (Runnable r : onCancel) r.run();
    }

    @Override
    public final void tick() {
        for (Runnable r : onTick) r.run();
    }

    public final void addTickActivity(Runnable r) {
        onTick.add(r);
    }

    public final void addCancelActivity(Runnable r) {
        onCancel.add(r);
    }
}