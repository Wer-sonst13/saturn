package gg.nolimite.mixin;

import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Zugriff auf das geschützte addDrawableChild von Screen.
 * Geerbte Methoden lassen sich nicht per @Shadow in einer Mixin-Klasse
 * auf GameMenuScreen holen, deshalb gibt es dieses Invoker-Interface.
 */
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Invoker("addDrawableChild")
    <T extends Element & Drawable & Selectable> T nolimite$addDrawableChild(T element);
}