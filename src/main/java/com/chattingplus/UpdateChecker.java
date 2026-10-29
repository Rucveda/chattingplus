package com.chattingplus;

import com.google.gson.JsonElement;
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
 * 새 버전 안내. GitHub 의 최신 Release(초안·사전 출시 제외)를 확인해 태그(v1.13.0)가 지금 버전보다 새로우면
 * 내 채팅창에만 안내 문구를 띄운다. Release 설명의 첫 줄을 함께 보여준다. 자동으로 내려받거나 설치하지는 않는다.
 * 게임을 켠 뒤 서버에 처음 들어갈 때 한 번만 확인한다.
 */
final class UpdateChecker {
	static final String REPO = "rucveda/chattingplus";
	private static final String LATEST_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
	private static final int MAX_MESSAGE = 80;
	private static boolean checked;

	private UpdateChecker() {}

	record Release(String version, String url, String message) {}

	static void checkOnce() {
		if (checked) return;
		checked = true;
		if (!ModConfig.get().checkUpdates) return;

		HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_URL))
				.timeout(Duration.ofSeconds(8))
				.header("Accept", "application/vnd.github+json")
				// GitHub API 는 User-Agent 가 없으면 요청을 거절한다
				.header("User-Agent", "ChattingPlus/" + currentVersion())
				.GET().build();
		HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
				.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(response -> {
					// 아직 Release 가 없으면 404
					if (response.statusCode() != 200) return;
					Release release = parse(response.body());
					if (release == null || compare(release.version(), currentVersion()) <= 0) return;
					MinecraftClient client = MinecraftClient.getInstance();
					client.execute(() -> notify(client, release));
				})
				.exceptionally(e -> {
					ChattingPlusClient.LOGGER.warn("[ChattingPlus] update check failed: {}", e.toString());
					return null;
				});
	}

	/** GitHub releases/latest 응답에서 버전, 페이지 주소, 설명 첫 줄을 꺼낸다. 형식이 다르면 null */
	static Release parse(String body) {
		try {
			JsonObject json = JsonParser.parseString(body).getAsJsonObject();
			String tag = string(json, "tag_name");
			if (tag == null || tag.isBlank()) return null;
			String version = tag.trim().replaceFirst("^[vV]", "");
			String url = string(json, "html_url");
			if (url == null || !url.startsWith("https://github.com/")) url = "https://github.com/" + REPO + "/releases/latest";
			return new Release(version, url, firstLine(string(json, "body")));
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String string(JsonObject json, String key) {
		JsonElement e = json.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	/** 마크다운 설명에서 처음 나오는 글자 있는 줄. 머리말(#)과 목록 기호(-, *)는 뗀다 */
	private static String firstLine(String body) {
		if (body == null) return null;
		for (String line : body.split("\\R")) {
			String s = line.replaceFirst("^\\s*(#+|[-*])\\s*", "").trim();
			if (s.isEmpty()) continue;
			return s.length() > MAX_MESSAGE ? s.substring(0, MAX_MESSAGE - 1) + "…" : s;
		}
		return null;
	}

	private static void notify(MinecraftClient client, Release release) {
		MutableText text = Text.translatable("chattingplus.update.available", release.version(), currentVersion()).formatted(Formatting.YELLOW);
		if (release.message() != null) text.append(Text.literal(" " + release.message()).formatted(Formatting.GRAY));
		text.append(Text.literal(" "));
		text.append(Text.translatable("chattingplus.update.download").styled(s -> s
				.withColor(Formatting.AQUA).withUnderline(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, release.url()))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(release.url())))));
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
			int x = i < pa.length ? parse(pa[i], 0) : 0, y = i < pb.length ? parse(pb[i], 0) : 0;
			if (x != y) return Integer.compare(x, y);
		}
		return 0;
	}

	private static int parse(String s, int fallback) {
		try {
			return Integer.parseInt(s.replaceAll("\\D", ""));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
