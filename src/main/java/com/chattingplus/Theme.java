package com.chattingplus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 모드 창과 팝업의 색. 색은 ARGB.
 * 미리 만든 테마(다크/핑크/메신저/DM)는 바꿀 수 없고, 사용자가 만든 테마(id 가 custom_ 으로 시작)는 바꿀 수 있다.
 * 글자 크기나 여백 같은 크기 값은 테마와 상관없이 {@link ModConfig.Sizes} 에 있다.
 */
public class Theme {
	private static final String CUSTOM_PREFIX = "custom_";

	/** 사용자가 만든 테마만 쓴다 (미리 만든 테마는 PRESETS 의 키가 id) */
	public String id;
	public String name;

	/** 목록 쪽 배경 */
	public int windowBg;
	/** 대화 내용 쪽 배경 */
	public int chatBg;
	/** 머리글, 팝업 제목줄과 탭 */
	public int headerBg;
	public int divider;
	public int text;
	/** 시간, 미리보기 같은 보조 글자 */
	public int mutedText;
	/** 안 읽음 배지, 새 귓속말 탭 깜빡임 */
	public int accent;
	public int mineBubble;
	public int mineText;
	public int theirsBubble;
	public int theirsText;
	/** 밝은 테마에서는 글자 그림자가 지저분해 보여서 끈다 */
	public boolean textShadow = true;
	/** 버튼/입력칸/슬라이더를 마인크래프트 기본 모양으로 그린다. 끄면 테마 색을 따르는 둥근 평면 모양 */
	public boolean vanillaWidgets = true;
	/** 고른 대화, 입력 중인 칸, 버튼을 가리킬 때의 강조색. 비어 있으면(예전 설정) 알림 강조색 */
	public Integer selectAccent;

	public int selection() {
		return selectAccent != null ? selectAccent : accent;
	}

	/** 채널 이름 색. 비어 있으면(예전 설정) 바탕 밝기에 맞는 기본색 */
	public Integer channelGlobal;
	public Integer channelLocal;
	public Integer channelVillage;

	/** 바탕이 밝은 테마인지 */
	public boolean light() {
		int c = windowBg;
		return ((c >> 16) & 0xFF) * 299 + ((c >> 8) & 0xFF) * 587 + (c & 0xFF) * 114 > 128_000;
	}

	public int channelColor(Channel channel) {
		Integer stored = switch (channel) {
			case GLOBAL -> channelGlobal;
			case LOCAL -> channelLocal;
			case VILLAGE -> channelVillage;
		};
		if (stored != null) return stored;
		boolean light = light();
		return switch (channel) {
			case GLOBAL -> light ? 0xFF2F6FB3 : 0xFF8EC5FF;
			case LOCAL -> light ? 0xFF2E8540 : 0xFF8EDB8A;
			case VILLAGE -> light ? 0xFFB86A00 : 0xFFFFC46B;
		};
	}

	public void setChannelColor(Channel channel, int color) {
		switch (channel) {
			case GLOBAL -> channelGlobal = color;
			case LOCAL -> channelLocal = color;
			case VILLAGE -> channelVillage = color;
		}
	}

	public Theme copy() {
		Theme t = new Theme();
		t.windowBg = windowBg;
		t.chatBg = chatBg;
		t.headerBg = headerBg;
		t.divider = divider;
		t.text = text;
		t.mutedText = mutedText;
		t.accent = accent;
		t.mineBubble = mineBubble;
		t.mineText = mineText;
		t.theirsBubble = theirsBubble;
		t.theirsText = theirsText;
		t.textShadow = textShadow;
		t.vanillaWidgets = vanillaWidgets;
		t.selectAccent = selection();
		t.channelGlobal = channelColor(Channel.GLOBAL);
		t.channelLocal = channelColor(Channel.LOCAL);
		t.channelVillage = channelColor(Channel.VILLAGE);
		return t;
	}

	// ---------------------------------------------------------------- 미리 만든 테마

	/** id → 테마. 순서대로 설정 화면 목록에 나온다. */
	public static final Map<String, Theme> PRESETS = new LinkedHashMap<>();

	static {
		Theme dark = new Theme();
		dark.windowBg = 0xE8121418;
		dark.chatBg = 0xE81B1E24;
		dark.headerBg = 0xFF2B303B;
		dark.divider = 0xFF2C3038;
		dark.text = 0xFFFFFFFF;
		dark.mutedText = 0xFF8A8F98;
		dark.accent = 0xFFE5484D;
		dark.selectAccent = 0xFF5B8DEF;
		dark.mineBubble = 0xFFFEE500;
		dark.mineText = 0xFF1A1A1A;
		dark.theirsBubble = 0xFF3B3F47;
		dark.theirsText = 0xFFFFFFFF;
		add("dark", "다크", dark);

		Theme pink = new Theme();
		pink.windowBg = 0xF0FFF2F7;
		pink.chatBg = 0xF0FFE6EF;
		pink.headerBg = 0xFFF9C3D6;
		pink.divider = 0xFFF2C6D6;
		pink.text = 0xFF5A2A3C;
		pink.mutedText = 0xFFB07A8E;
		pink.accent = 0xFFFF4F8B;
		pink.selectAccent = 0xFFE36A9A;
		pink.mineBubble = 0xFFFF8FB4;
		pink.mineText = 0xFFFFFFFF;
		pink.theirsBubble = 0xFFFFFFFF;
		pink.theirsText = 0xFF5A2A3C;
		pink.textShadow = false;
		pink.vanillaWidgets = false;
		add("pink", "핑크", pink);

		Theme messenger = new Theme();
		messenger.windowBg = 0xF0F7F8FA;
		messenger.chatBg = 0xF0B2C7D9;
		messenger.headerBg = 0xFFA4B9CC;
		messenger.divider = 0xFF9DB0C2;
		messenger.text = 0xFF1E1E1E;
		messenger.mutedText = 0xFF5B6B7A;
		messenger.accent = 0xFFFF5A5A;
		messenger.selectAccent = 0xFF3D7BD9;
		messenger.mineBubble = 0xFFFEE500;
		messenger.mineText = 0xFF1A1A1A;
		messenger.theirsBubble = 0xFFFFFFFF;
		messenger.theirsText = 0xFF1A1A1A;
		messenger.textShadow = false;
		messenger.vanillaWidgets = false;
		add("messenger", "메신저", messenger);

		// 검은 바탕에 파란 내 말풍선, 짙은 회색 상대 말풍선
		Theme dm = new Theme();
		dm.windowBg = 0xF2000000;
		dm.chatBg = 0xF2000000;
		dm.headerBg = 0xFF121212;
		dm.divider = 0xFF262626;
		dm.text = 0xFFF5F5F5;
		dm.mutedText = 0xFFA8A8A8;
		dm.accent = 0xFFFF3040;
		dm.selectAccent = 0xFF0095F6;
		dm.mineBubble = 0xFF3797F0;
		dm.mineText = 0xFFFFFFFF;
		dm.theirsBubble = 0xFF262626;
		dm.theirsText = 0xFFF5F5F5;
		dm.textShadow = false;
		dm.vanillaWidgets = false;
		add("dm", "DM", dm);
	}

	private static void add(String id, String name, Theme theme) {
		theme.id = id;
		theme.name = name;
		PRESETS.put(id, theme);
	}

	// ---------------------------------------------------------------- 고르기, 만들기, 지우기

	/** 지금 쓰는 테마 */
	public static Theme get() {
		Theme theme = byId(ModConfig.get().themePreset);
		return theme != null ? theme : PRESETS.get("dark");
	}

	public static Theme byId(String id) {
		if (id == null) return null;
		if (PRESETS.containsKey(id)) return PRESETS.get(id);
		for (Theme t : ModConfig.get().customThemes) if (id.equals(t.id)) return t;
		return null;
	}

	/** 미리 만든 테마 다음에 사용자가 만든 테마 */
	public static List<Theme> all() {
		List<Theme> list = new ArrayList<>(PRESETS.values());
		list.addAll(ModConfig.get().customThemes);
		return list;
	}

	public boolean editable() {
		return id != null && id.startsWith(CUSTOM_PREFIX);
	}

	public static void select(String id) {
		ModConfig.get().themePreset = id;
		ModConfig.save();
	}

	/** base 를 복사해서 새 테마를 만들고 고른다 */
	public static Theme create(Theme base, String name) {
		ModConfig cfg = ModConfig.get();
		int next = 1;
		for (Theme t : cfg.customThemes) {
			try {
				next = Math.max(next, Integer.parseInt(t.id.substring(CUSTOM_PREFIX.length())) + 1);
			} catch (RuntimeException ignored) {
			}
		}
		Theme theme = base.copy();
		theme.id = CUSTOM_PREFIX + next;
		theme.name = name;
		cfg.customThemes.add(theme);
		select(theme.id);
		return theme;
	}

	public static void delete(Theme theme) {
		ModConfig cfg = ModConfig.get();
		cfg.customThemes.remove(theme);
		if (theme.id.equals(cfg.themePreset)) cfg.themePreset = "dark";
		ModConfig.save();
	}

	// ---------------------------------------------------------------- 색 계산

	/** a 와 b 를 t(0~1) 만큼 섞는다. 알파는 a 를 따른다. */
	public static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
	}

	public static int withAlpha(int color, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
	}

	public int selected() {
		return mix(windowBg | 0xFF000000, text, 0.14f);
	}

	public int hover() {
		return withAlpha(text, 0x22);
	}

	/** 탭 바탕: 이웃한 탭끼리 명도를 살짝 다르게 */
	public int tab(int index) {
		return mix(headerBg, chatBg | 0xFF000000, index % 2 == 0 ? 0.55f : 0.35f);
	}

	public int dayChip() {
		return withAlpha(mix(chatBg, text, 0.5f), 0x40);
	}

	/** 팝업은 채팅창이 닫혀 있으면 설정한 불투명도만큼만 보이게 */
	public int popupAlpha(int color, boolean interactive) {
		int alpha = (color >>> 24) & 0xFF;
		if (!interactive) alpha = alpha * Math.max(10, Math.min(100, ModConfig.get().sizes.popupOpacity)) / 100;
		return withAlpha(color, alpha);
	}
}
