package com.chattingplus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SettingsShareTest {
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
