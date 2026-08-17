package cn.sulyhub.linguacraft.client;

import cn.sulyhub.linguacraft.client.config.LinguaCraftConfig;
import cn.sulyhub.linguacraft.client.translate.TranslationManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class LinguaCraftClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("LinguaCraft/Client");
	private static KeyMapping toggleKey;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing LinguaCraft Client v1.0.1...");

		// 1. Load config
		LinguaCraftConfig config = LinguaCraftConfig.getInstance();
		LOGGER.info("LinguaCraft configuration loaded. Provider: {}, Model: {}, Target Language: {}", 
				config.provider, config.model, config.targetLanguage);

		// 2. Initialize TranslationManager
		TranslationManager manager = TranslationManager.getInstance();

		// 3. Register Tooltip Translation Callback (with Hold-Tab-to-Peek feature)
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			LinguaCraftConfig currentConfig = LinguaCraftConfig.getInstance();
			if (!currentConfig.enabled || !currentConfig.translateTooltips) {
				return;
			}

			// Check if Tab (or Left Alt) key is being held down
			boolean isTabHeld = false;
			try {
				Minecraft mc = Minecraft.getInstance();
				if (mc.getWindow() != null) {
					isTabHeld = InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_TAB)
							 || InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LALT);
				}
			} catch (Exception ignored) {}

			List<String> uncachedTexts = new ArrayList<>();
			boolean hasTranslatableContent = false;

			for (int i = 0; i < lines.size(); i++) {
				Component original = lines.get(i);
				String plain = original.getString();
				if (plain == null || plain.isBlank() || manager.shouldSkip(TranslationManager.stripFormatting(plain).trim())) {
					continue;
				}

				hasTranslatableContent = true;

				if (isTabHeld && currentConfig.holdTabToShowOriginal) {
					// When holding Tab: show original text as-is, but collect for prefetching
					String clean = TranslationManager.stripFormatting(plain).trim();
					String cacheKey = currentConfig.provider.name() + ":" + currentConfig.targetLanguage + ":" + clean;
					if (!manager.getCache().contains(cacheKey)) {
						uncachedTexts.add(plain);
					}
				} else {
					// Default: replace with translated text if available
					String translated = manager.getOrRequest(plain, null);
					if (!translated.equals(plain)) {
						lines.set(i, Component.literal(translated).withStyle(original.getStyle()));
					} else {
						uncachedTexts.add(plain);
					}
				}
			}

			// Add interactive hint line at the bottom if item has translatable lines
			if (hasTranslatableContent && currentConfig.showHintLine && currentConfig.holdTabToShowOriginal) {
				if (isTabHeld) {
					lines.add(Component.literal("§8[松开 Tab 查看译文]"));
				} else {
					lines.add(Component.literal("§8[按住 Tab 查看原文]"));
				}
			}

			// Batch queue any uncached lines in a single HTTP request to leverage DeepSeek Context Cache
			if (!uncachedTexts.isEmpty()) {
				manager.queueBatch(uncachedTexts, null);
			}
		});

		// 4. Register Toggle KeyMapping (Default: F8)
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("linguacraft", "general"));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.linguacraft.toggle",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_F8,
				category
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				LinguaCraftConfig cfg = LinguaCraftConfig.getInstance();
				cfg.enabled = !cfg.enabled;
				cfg.save();

				if (client.player != null) {
					String status = cfg.enabled ? "§a已开启" : "§c已关闭";
					client.player.sendOverlayMessage(
							Component.literal("§b[LinguaCraft] §r翻译功能 " + status)
					);
				}
			}
		});

		// 5. Register In-Game Commands: /linguacraft
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
					ClientCommands.literal("linguacraft")
							// /linguacraft setkey <apiKey>
							.then(ClientCommands.literal("setkey")
									.then(ClientCommands.argument("apiKey", StringArgumentType.greedyString())
											.executes(context -> {
												String key = StringArgumentType.getString(context, "apiKey").trim();
												LinguaCraftConfig cfg = LinguaCraftConfig.getInstance();
												cfg.apiKey = key;
												cfg.save();
												manager.getCache().clear();

												String maskedKey = key.length() > 8 
														? key.substring(0, 4) + "..." + key.substring(key.length() - 4) 
														: "***";
												context.getSource().sendFeedback(
														Component.literal("§b[LinguaCraft] §aAPI Key 已更新并保存: §e" + maskedKey)
												);
												return 1;
											})
									)
							)
							// /linguacraft status
							.then(ClientCommands.literal("status")
									.executes(context -> {
										LinguaCraftConfig cfg = LinguaCraftConfig.getInstance();
										String keyStatus = (cfg.apiKey != null && !cfg.apiKey.isBlank())
												? (cfg.apiKey.length() > 8 
														? "§a已配置 (" + cfg.apiKey.substring(0, 4) + "..." + cfg.apiKey.substring(cfg.apiKey.length() - 4) + ")"
														: "§a已配置")
												: "§c未配置 (请使用 /linguacraft setkey <你的KEY>)";

										context.getSource().sendFeedback(Component.literal("§b====== [LinguaCraft 状态] ======"));
										context.getSource().sendFeedback(Component.literal("§f翻译开关: " + (cfg.enabled ? "§a已开启 (按 F8 切换)" : "§c已关闭 (按 F8 切换)")));
										context.getSource().sendFeedback(Component.literal("§f当前提供商: §e" + cfg.provider));
										context.getSource().sendFeedback(Component.literal("§f当前模型: §e" + cfg.model));
										context.getSource().sendFeedback(Component.literal("§fAPI Key: " + keyStatus));
										context.getSource().sendFeedback(Component.literal("§f按住 Tab 查看原文: §e" + (cfg.holdTabToShowOriginal ? "已启用" : "未启用")));
										context.getSource().sendFeedback(Component.literal("§f本地缓存条数: §e" + manager.getCache().size() + " 条"));
										context.getSource().sendFeedback(Component.literal("§b============================="));
										return 1;
									})
							)
							// /linguacraft provider <DEEPSEEK_RESPONSES|DEEPL|OPENAI_CHAT>
							.then(ClientCommands.literal("provider")
									.then(ClientCommands.argument("providerName", StringArgumentType.word())
											.executes(context -> {
												String pStr = StringArgumentType.getString(context, "providerName").toUpperCase();
												try {
													LinguaCraftConfig.Provider p = LinguaCraftConfig.Provider.valueOf(pStr);
													LinguaCraftConfig cfg = LinguaCraftConfig.getInstance();
													cfg.provider = p;
													cfg.save();
													manager.getCache().clear();
													context.getSource().sendFeedback(
															Component.literal("§b[LinguaCraft] §a提供商已切换为: §e" + p)
													);
												} catch (IllegalArgumentException e) {
													context.getSource().sendFeedback(
															Component.literal("§c[LinguaCraft] 未知提供商! 可选项: DEEPSEEK_RESPONSES, DEEPL, OPENAI_CHAT")
													);
												}
												return 1;
											})
									)
							)
							// /linguacraft clearcache
							.then(ClientCommands.literal("clearcache")
									.executes(context -> {
										manager.getCache().clear();
										context.getSource().sendFeedback(
												Component.literal("§b[LinguaCraft] §a本地翻译缓存已清空。")
										);
										return 1;
									})
							)
							// /linguacraft reload
							.then(ClientCommands.literal("reload")
									.executes(context -> {
										LinguaCraftConfig.load();
										manager.getCache().clear();
										context.getSource().sendFeedback(
												Component.literal("§b[LinguaCraft] §a配置文件已重新加载。")
										);
										return 1;
									})
							)
			);
		});

		LOGGER.info("LinguaCraft Client initialized successfully!");
	}
}