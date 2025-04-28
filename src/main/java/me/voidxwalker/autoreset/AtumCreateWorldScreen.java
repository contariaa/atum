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
        return job;
    }

    public enum Job {
        CREATION,
        CONFIGURATION
    }
}
