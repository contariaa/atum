package me.voidxwalker.autoreset.mixin.access;

import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreateWorldScreen.Mode.class)
public interface CreateWorldScreen$ModeAccessor {
    @Accessor("translationSuffix")
    String atum$getTranslationSuffix();
}
