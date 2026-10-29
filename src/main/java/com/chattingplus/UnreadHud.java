package com.chattingplus;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/** 게임 화면 오른쪽 아래에 새 귓속말 미리보기와 안 읽은 개수를 띄운다. */
public final class UnreadHud {
	private static final long PREVIEW_MS = 5000;

	private static WhisperParser.Whisper lastIncoming;
	private static long lastIncomingAt;

	private UnreadHud() {}

	static void notifyIncoming(WhisperParser.Whisper whisper) {
		lastIncoming = whisper;
		lastIncomingAt = System.currentTimeMillis();
	}

	static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!ModConfig.get().showHud || client.options.hudHidden || client.currentScreen instanceof WhisperScreen) return;

		int unread = WhisperStore.get().totalUnread();
		if (unread == 0) return;

		TextRenderer tr = client.textRenderer;
		String key = ChattingPlusClient.openKey().getBoundKeyLocalizedText().getString();
		Text summary = Text.translatable("chattingplus.hud.unread", unread, key);
		int right = context.getScaledWindowWidth() - 6;
		int y = context.getScaledWindowHeight() - 18;

		drawLabel(context, tr, summary, right, y, 0xFFFFE066);

		if (lastIncoming != null && System.currentTimeMillis() - lastIncomingAt < PREVIEW_MS) {
			String preview = tr.trimToWidth(lastIncoming.partner() + ": " + lastIncoming.text(), 200);
			drawLabel(context, tr, Text.literal(preview), right, y - 14, 0xFFFFFFFF);
		}
	}

	private static void drawLabel(DrawContext context, TextRenderer tr, Text text, int right, int y, int color) {
		int width = tr.getWidth(text);
		int x = right - width;
		context.fill(x - 4, y - 3, right + 3, y + 11, 0xB0000000);
		context.drawTextWithShadow(tr, text, x, y, color);
	}
}
