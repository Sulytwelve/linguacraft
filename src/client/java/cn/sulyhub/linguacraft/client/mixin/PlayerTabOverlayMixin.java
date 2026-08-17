package cn.sulyhub.linguacraft.client.mixin;

import cn.sulyhub.linguacraft.client.config.LinguaCraftConfig;
import cn.sulyhub.linguacraft.client.translate.TranslationManager;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    @ModifyVariable(method = "setHeader", at = @At("HEAD"), argsOnly = true)
    private Component onSetHeader(Component header) {
        LinguaCraftConfig config = LinguaCraftConfig.getInstance();
        if (header != null && config.enabled && config.translateTabMenu) {
            String plain = header.getString();
            String translated = TranslationManager.getInstance().getOrRequest(plain, null);
            if (!translated.equals(plain)) {
                return Component.literal(translated).withStyle(header.getStyle());
            }
        }
        return header;
    }

    @ModifyVariable(method = "setFooter", at = @At("HEAD"), argsOnly = true)
    private Component onSetFooter(Component footer) {
        LinguaCraftConfig config = LinguaCraftConfig.getInstance();
        if (footer != null && config.enabled && config.translateTabMenu) {
            String plain = footer.getString();
            String translated = TranslationManager.getInstance().getOrRequest(plain, null);
            if (!translated.equals(plain)) {
                return Component.literal(translated).withStyle(footer.getStyle());
            }
        }
        return footer;
    }
}
