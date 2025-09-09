package me.voidxwalker.autoreset.mixin.hotkey;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.voidxwalker.autoreset.Atum;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.apache.commons.lang3.ArrayUtils;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Objects;

@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {

    @WrapOperation(
            method = "<init>(Lnet/minecraft/client/MinecraftClient;Ljava/io/File;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/option/GameOptions;allKeys:[Lnet/minecraft/client/option/KeyBinding;",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void addResetKey(GameOptions options, KeyBinding[] allKeys, Operation<Void> original) {
        original.call(options, ArrayUtils.add(allKeys, Objects.requireNonNull(Atum.resetKey)));
    }
}
