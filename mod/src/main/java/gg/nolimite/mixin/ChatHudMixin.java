package gg.nolimite.mixin;

import gg.nolimite.Effects;
import gg.nolimite.Module;
import gg.nolimite.Trackers;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * CHAT UTILS: merkt sich jede Nachricht zum Suchen und Kopieren.
 * TIMESTAMPS: stellt jeder Nachricht die Uhrzeit voran.
 *
 * Die Nachricht wird als Argument ersetzt statt die Methode abzubrechen,
 * sonst ginge der normale Chat-Eintrag verloren.
 */
@Mixin(ChatHud.class)
public class ChatHudMixin {

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private Text nolimite$modify(Text message) {
        if (message == null) return null;
        String raw = message.getString();
        Trackers.addChat(raw);

        if (!Effects.isOn("timestamps")) return message;
        Module m = Module.get("timestamps");
        String format = m == null ? "[HH:mm]" : m.choice("format", "[HH:mm]");
        String stamp = Trackers.clock(format.contains("ss"), false);
        String prefix = format.startsWith("[") ? stamp : stamp + " ";
        return Text.literal(prefix + raw);
    }
}