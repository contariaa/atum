package me.voidxwalker.autoreset;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import org.jetbrains.annotations.Nullable;

public class AtumCreateWorldScreen extends CreateWorldScreen {
    private final Job job;

    public AtumCreateWorldScreen(@Nullable Screen parent) {
        this(parent, Job.CREATION);
    }

    public AtumCreateWorldScreen(@Nullable Screen parent, Job job) {
        super(parent);
        this.job = job;
    }

    public Job getJob() {
        return this.job;
    }

    @Override
    public boolean shouldPauseGame() {
        return this.job != Job.CREATION;
    }

    public enum Job {
        CREATION,
        CONFIGURATION
    }
}
