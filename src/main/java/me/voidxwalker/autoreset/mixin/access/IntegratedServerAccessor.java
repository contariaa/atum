package me.voidxwalker.autoreset.mixin.access;

import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.level.LevelInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IntegratedServer.class)
public interface IntegratedServerAccessor {
    @Accessor("levelInfo")
    LevelInfo atum$getLevelInfo();
}
