package me.voidxwalker.autoreset.api.seedprovider;

import me.voidxwalker.autoreset.Atum;
import me.voidxwalker.autoreset.AtumCreateWorldScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public abstract class AtumWaitingScreen extends Screen {
    private boolean decided = false;

    protected AtumWaitingScreen(Text title) {
        super(title);
    }

    @SuppressWarnings("unused")
    protected final void cancelWorldCreation() {
        this.onClose();
    }

    @SuppressWarnings("unused")
    protected final void continueWorldCreation() {
        this.onDecided();
        MinecraftClient.getInstance().openScreen(AtumCreateWorldScreen.create(null));
    }

    private void onDecided() {
        Atum.ensureState(!this.decided, "AtumWaitingScreen continue method(s) called more than once!");
        this.decided = true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public final void onClose() {
        this.onDecided();
        Atum.stopRunning();
        super.onClose();
    }

    @Override
    public final void removed() {
        Atum.ensureState(this.decided, "Improper closing of AtumWaitingScreen. Methods continueWorldCreation or cancelWorldCreation should be used.");
        onRemoved();
    }

    protected void onRemoved() {
    }
}
