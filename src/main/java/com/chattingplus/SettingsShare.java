package com.chattingplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.math.MathHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 설정 공유 코드. 지금 테마(색, 글자 그림자, 버튼 모양)와 크기 설정을 한 줄짜리 글자로 바꾸고, 다시 읽는다.
 * 형식: "CP1:" + base64url(gzip(JSON)). 명령어, 내 닉네임, 팝업 위치, 대화 기록 같은 개인 설정은 넣지 않는다.
 */
final class SettingsShare {
	private static final String PREFIX = "CP1:";
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	/** 너무 큰 글자를 붙여넣었을 때 압축 풀기에서 멈추지 않도록 */
	private static final int MAX_JSON = 64 * 1024;

	/** 가져오기 결과: 새로 만든 테마 이름 */
	record Imported(String themeName) {}

	private SettingsShare() {}

	static String export() {
		JsonObject root = new JsonObject();
		root.addProperty("v", 1);
		Theme theme = Theme.get().copy();
		theme.id = null;
		theme.name = Theme.get().name;
		root.add("theme", GSON.toJsonTree(theme));
		root.add("sizes", GSON.toJsonTree(ModConfig.get().sizes));
		byte[] json = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
				gz.write(json);
			}
			return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 코드를 읽어 새 테마로 추가하고 고른 뒤, 크기 설정을 적용한다.
	 * @throws IllegalArgumentException 코드가 아니거나 깨졌을 때
	 */
	static Imported apply(String code) {
		String text = code == null ? "" : code.trim();
		if (!text.startsWith(PREFIX)) throw new IllegalArgumentException("not a code");
		JsonObject root;
		try {
			byte[] packed = Base64.getUrlDecoder().decode(text.substring(PREFIX.length()));
			try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(packed))) {
				byte[] json = gz.readNBytes(MAX_JSON + 1);
				if (json.length > MAX_JSON) throw new IllegalArgumentException("too large");
				root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
			}
		} catch (IOException | RuntimeException e) {
			throw new IllegalArgumentException("broken code", e);
		}

		Theme theme = GSON.fromJson(root.get("theme"), Theme.class);
		if (theme == null) throw new IllegalArgumentException("no theme");
		String name = theme.name == null || theme.name.isBlank() ? "공유 테마" : theme.name.trim();
		if (name.length() > 24) name = name.substring(0, 24);
		Theme created = Theme.create(theme, name);

		if (root.has("sizes")) {
			ModConfig.Sizes s = GSON.fromJson(root.get("sizes"), ModConfig.Sizes.class);
			if (s != null) ModConfig.get().sizes = clamp(s);
		}
		ModConfig.save();
		return new Imported(created.name);
	}

	/** 설정 화면의 슬라이더 범위를 벗어난 값은 범위 안으로 */
	private static ModConfig.Sizes clamp(ModConfig.Sizes s) {
		s.textScale = MathHelper.clamp(s.textScale, 0.5f, 2.0f);
		s.listNameScale = MathHelper.clamp(s.listNameScale, 0.75f, 1.5f);
		s.listSubScale = MathHelper.clamp(s.listSubScale, 0.75f, 1.5f);
		s.titleScale = MathHelper.clamp(s.titleScale, 0.75f, 1.5f);
		s.metaScale = MathHelper.clamp(s.metaScale, 0.75f, 1.5f);
		s.popupScale = MathHelper.clamp(s.popupScale, 0.75f, 1.5f);
		s.bubblePadding = MathHelper.clamp(s.bubblePadding, 1, 10);
		s.messageGap = MathHelper.clamp(s.messageGap, 0, 12);
		s.stickerSize = MathHelper.clamp(s.stickerSize, 24, 96);
		s.popupOpacity = MathHelper.clamp(s.popupOpacity, 10, 100);
		s.cornerRadius = MathHelper.clamp(s.cornerRadius, 0, 10);
		return s;
	}
}
