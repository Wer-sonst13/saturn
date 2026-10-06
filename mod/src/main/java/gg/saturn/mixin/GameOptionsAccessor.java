package gg.saturn.mixin;

import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Zugriff auf die private Gamma-Option (Helligkeit). */
@Mixin(GameOptions.class)
public interface GameOptionsAccessor {

    @Accessor("gamma")
    net.minecraft.client.option.SimpleOption<Double> saturn$gamma();
}