package com.chattingplus;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SettingsShareTest {
	/**
	 * Chatting Plus 와 Together Plus 가 주고받는 공유 코드의 JSON. **두 모드의 테스트에 글자 하나 다르지 않게 똑같이 둔다**
	 * (chattingplus SettingsShareTest, togetherplus ThemeShareTest). Theme·Sizes 필드를 바꾸면 양쪽을 같이 고쳐야 테스트가 통과한다
	 */
	static final String SHARED_JSON = "{\"v\":1,\"theme\":{\"name\":\"우리 테마\",\"windowBg\":-251658241,\"chatBg\":-251663633,"
			+ "\"headerBg\":-408618,\"divider\":-867626,\"text\":-10868164,\"mutedText\":-5211506,\"accent\":-45173,"
			+ "\"mineBubble\":-28748,\"mineText\":-1,\"theirsBubble\":-1,\"theirsText\":-10868164,\"textShadow\":false,"
			+ "\"vanillaWidgets\":false,\"selectAccent\":-1873254,\"channelGlobal\":-13668429,\"channelLocal\":-16733696,"
			+ "\"channelVillage\":-26368},"
			+ "\"sizes\":{\"textScale\":1.25,\"bubblePadding\":4,\"messageGap\":4,\"stickerSize\":48,\"popupOpacity\":80,"
			+ "\"cornerRadius\":6,\"listNameScale\":1.0,\"listSubScale\":1.0,\"titleScale\":1.0,\"metaScale\":1.0,\"popupScale\":1.0}}";

	static String code(String json) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
			gz.write(json.getBytes(StandardCharsets.UTF_8));
		}
		return "CP1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
	}

	/** 클래스의 저장되는 필드 이름 (id 는 공유 코드에 넣지 않는다) */
	private static Set<String> fieldNames(Class<?> type) {
		Set<String> names = new TreeSet<>();
		for (Field f : type.getDeclaredFields()) {
			int mod = f.getModifiers();
			if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.getName().equals("id")) continue;
			names.add(f.getName());
		}
		return names;
	}

	@Test
	void fieldsMatchSharedCode() {
		// 한쪽 모드에만 필드를 더하거나 이름을 바꾸면 여기서 걸린다
		JsonObject root = JsonParser.parseString(SHARED_JSON).getAsJsonObject();
		assertEquals(new TreeSet<>(root.getAsJsonObject("theme").keySet()), fieldNames(Theme.class));
		assertEquals(new TreeSet<>(root.getAsJsonObject("sizes").keySet()), fieldNames(ModConfig.Sizes.class));
	}

	@Test
	void readsSharedCode() throws Exception {
		ModConfig cfg = ModConfig.get();
		assertEquals("우리 테마", SettingsShare.apply(code(SHARED_JSON)).themeName());
		Theme theme = Theme.get();
		assertEquals(0xF0FFFFFF, theme.windowBg);
		assertEquals(-1873254, theme.selection());
		assertFalse(theme.textShadow);
		assertEquals(6, cfg.sizes.cornerRadius);
		assertEquals(80, cfg.sizes.popupOpacity);
		assertEquals(1.25f, cfg.sizes.textScale);
		cfg.sizes = new ModConfig.Sizes();
	}

	@Test
	void codeRoundTripRestoresThemeAndSizes() {
		ModConfig cfg = ModConfig.get();
		cfg.themePreset = "messenger";
		cfg.sizes.textScale = 1.25f;
		cfg.sizes.cornerRadius = 7;
		String code = SettingsShare.export();
		assertTrue(code.startsWith("CP1:"));

		// 다른 값으로 바꿔 둔 뒤 가져오면 원래 값이 새 테마로 돌아와야 한다
		cfg.themePreset = "dark";
		cfg.sizes.textScale = 1f;
		cfg.sizes.cornerRadius = 0;
		int before = cfg.customThemes.size();

		SettingsShare.Imported imported = SettingsShare.apply(code);
		assertEquals("메신저", imported.themeName());
		assertEquals(before + 1, cfg.customThemes.size());
		Theme theme = Theme.get();
		assertTrue(theme.editable(), "가져온 테마는 고칠 수 있는 새 테마");
		assertEquals(Theme.PRESETS.get("messenger").mineBubble, theme.mineBubble);
		assertEquals(1.25f, cfg.sizes.textScale);
		assertEquals(7, cfg.sizes.cornerRadius);
	}

	@Test
	void outOfRangeSizesAreClamped() {
		ModConfig cfg = ModConfig.get();
		cfg.sizes.textScale = 9f;
		cfg.sizes.cornerRadius = 99;
		String code = SettingsShare.export();
		SettingsShare.apply(code);
		assertEquals(2.0f, cfg.sizes.textScale);
		assertEquals(10, cfg.sizes.cornerRadius);
		cfg.sizes = new ModConfig.Sizes();
	}

	@Test
	void notACodeIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> SettingsShare.apply("hello"));
		assertThrows(IllegalArgumentException.class, () -> SettingsShare.apply("CP1:@@@@"));
		assertThrows(IllegalArgumentException.class, () -> SettingsShare.apply("CP1:"));
		assertThrows(IllegalArgumentException.class, () -> SettingsShare.apply(null));
	}
}
