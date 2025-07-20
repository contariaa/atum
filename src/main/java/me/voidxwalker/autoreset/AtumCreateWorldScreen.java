package me.voidxwalker.autoreset;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import org.jetbrains.annotations.Nullable;

public class AtumCreateWorldScreen extends CreateWorldScreen {
    private final Job job;
    private final boolean shouldShowLegalWarning;

    public AtumCreateWorldScreen(@Nullable Screen parent) {
        this(parent, Job.CREATION);
    }

    public AtumCreateWorldScreen(@Nullable Screen parent, Job job) {
        this(parent, job, job == Job.CONFIGURATION);
    }

    public AtumCreateWorldScreen(@Nullable Screen parent, Job job, boolean shouldShowLegalWarning) {
        super(parent);
        this.job = job;
        Atum.ensureState(job == Job.CONFIGURATION || !shouldShowLegalWarning, "shouldShowLegalWarning does not apply if the job is not CONFIGURATION!");
        this.shouldShowLegalWarning = shouldShowLegalWarning;
    }

    public Job getJob() {
        return this.job;
    }

    public boolean shouldShowLegalWarning() {
        return this.shouldShowLegalWarning;
    }

    public enum Job {
        CREATION,
        CONFIGURATION
    }
}
