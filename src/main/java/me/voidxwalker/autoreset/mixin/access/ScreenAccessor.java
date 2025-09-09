package me.voidxwalker.autoreset.mixin.access;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(Screen.class)
public interface ScreenAccessor {
    @Accessor("buttons")
    List<ButtonWidget> atum$getButtons();

    @Invoker("buttonClicked")
    void atum$buttonClicked(ButtonWidget button);
}
