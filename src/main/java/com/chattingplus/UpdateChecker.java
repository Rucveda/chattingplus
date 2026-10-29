package com.chattingplus;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 새 버전 안내. 설정의 updateUrl 에서 {"version":"1.6.0","download":"https://...","message":"이모티콘 8종 추가"} 를 받아
 * 지금 버전보다 새로우면 내 채팅창에만 안내 문구를 띄운다. 자동으로 내려받거나 설치하지는 않는다.
 * 게임을 켠 뒤 서버에 처음 들어갈 때 한 번만 확인한다.
 */
final class UpdateChecker {
	private static boolean checked;

	private UpdateChecker() {}

	static void checkOnce() {
		if (checked) return;
		checked = true;
		String url = ModConfig.get().updateUrl;
		if (url == null || url.isBlank()) return;

		HttpRequest request;
		try {
			request = HttpRequest.newBuilder(URI.create(url.trim())).timeout(Duration.ofSeconds(8)).GET().build();
		} catch (IllegalArgumentException e) {
			ChattingPlusClient.LOGGER.warn("[ChattingPlus] invalid updateUrl: {}", url);
			return;
		}
		HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
				.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(response -> {
					if (response.statusCode() != 200) return;
					JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
					String latest = json.get("version").getAsString();
					if (compare(latest, currentVersion()) <= 0) return;
					String download = json.has("download") ? json.get("download").getAsString() : null;
					String message = json.has("message") ? json.get("message").getAsString() : null;
					MinecraftClient client = MinecraftClient.getInstance();
					client.execute(() -> notify(client, latest, download, message));
				})
				.exceptionally(e -> {
					ChattingPlusClient.LOGGER.warn("[ChattingPlus] update check failed: {}", e.toString());
					return null;
				});
	}

	private static void notify(MinecraftClient client, String latest, String download, String message) {
		MutableText text = Text.translatable("chattingplus.update.available", latest, currentVersion()).formatted(Formatting.YELLOW);
		if (message != null && !message.isBlank()) text.append(Text.literal(" " + message).formatted(Formatting.GRAY));
		if (download != null && download.startsWith("http")) {
			text.append(Text.literal(" "));
			text.append(Text.translatable("chattingplus.update.download").styled(s -> s
					.withColor(Formatting.AQUA).withUnderline(true)
					.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, download))
					.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(download)))));
		}
		// 서버로 보내지 않고 내 채팅창에만 표시
		client.inGameHud.getChatHud().addMessage(Text.literal("[Chatting Plus] ").formatted(Formatting.GOLD).append(text));
	}

	static String currentVersion() {
		return FabricLoader.getInstance().getModContainer(ChattingPlusClient.MOD_ID)
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("0");
	}

	/** "1.10.0" 과 "1.9.2" 처럼 점으로 나눈 숫자를 비교 */
	static int compare(String a, String b) {
		String[] pa = a.split("[.\\-+]"), pb = b.split("[.\\-+]");
		for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
			int x = i < pa.length ? parse(pa[i]) : 0, y = i < pb.length ? parse(pb[i]) : 0;
			if (x != y) return Integer.compare(x, y);
		}
		return 0;
	}

	private static int parse(String s) {
		try {
			return Integer.parseInt(s.replaceAll("\\D", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
