package com.chattingplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** config/chattingplus.json 에 저장되는 설정. 게임을 끈 상태에서 직접 고쳐도 된다. */
public class ModConfig {
	/**
	 * 귓속말 한 줄을 해석하는 정규식. 다음 이름 그룹이 필요하다:
	 * from(보낸 사람), to(받는 사람), dir(보냄/받음 표시), msg(내용)
	 * 예: (Steve → Alex<보냄>) 안녕
	 * 서버가 줄 앞에 리소스팩 아이콘 문자를 붙이는 경우가 있어서, 한글/영문/숫자가 아닌 문자는 앞에 와도 된다.
	 */
	public static final String DEFAULT_PATTERN =
			"^[^\\p{IsHangul}A-Za-z0-9]*\\((?<from>.+?)\\s*(?:->|[→⇒➜➡⟶►▶»>])\\s*(?<to>.+?)\\s*<(?<dir>보냄|받음)>\\)\\s?(?<msg>.*)$";
	/** 예전 버전의 기본값. 설정 파일에 그대로 남아 있으면 새 기본값으로 바꾼다. */
	private static final List<String> OLD_DEFAULT_PATTERNS = List.of(
			"^\\((?<from>.+?)\\s*(?:->|[→⇒➜➡⟶►▶»>])\\s*(?<to>.+?)\\s*<(?<dir>보냄|받음)>\\)\\s?(?<msg>.*)$");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("chattingplus.json");
	private static ModConfig instance = new ModConfig();

	/** 귓속말 명령어 (슬래시 제외). 전송 시 "/{commandPrefix} 닉네임 내용" 으로 보낸다. */
	public String commandPrefix = "귓";
	/** dir 그룹이 이 값이면 내가 보낸 귓속말로 본다. */
	public String sentTag = "보냄";
	public String whisperPattern = DEFAULT_PATTERN;
	/** 인식한 귓속말을 기본 채팅창에서 숨긴다. */
	public boolean hideInChat = true;
	public boolean playSound = true;
	public boolean showHud = true;
	/** 대화 상대 한 명당 보관하는 최대 메시지 수 */
	public int maxMessagesPerConversation = 1000;

	public static class ChannelSettings {
		/** 채널 채팅 명령어 (슬래시 제외). 전송 시 "/{command} 내용" */
		public String command;
		/** 서버가 이 채널 줄 앞에 붙이는 아이콘 문자 */
		public String marker;
		/** 이 채널 메시지를 기본 채팅창에서 숨긴다 */
		public boolean hideInChat;

		ChannelSettings() {}

		ChannelSettings(Channel channel) {
			command = channel.defaultCommand;
			marker = channel.defaultMarker;
		}
	}

	/** 채널 id(global/local/village) → 설정 */
	public Map<String, ChannelSettings> channels = new LinkedHashMap<>();
	/** 채널 하나당 보관하는 최대 메시지 수 (전체 채팅은 양이 많아서 따로) */
	public int maxMessagesPerChannel = 500;
	/** 채널 채팅에 표시되는 내 닉네임. 모드로 채널 채팅을 보내면 자동으로 알아낸다. */
	public String myName = null;
	/**
	 * 귓속말로 인식한 줄과, "보냄/받음"이 들어 있는데 인식하지 못한 줄을 로그(latest.log)에 남긴다.
	 * 귓속말 내용이 파일에 남으므로 평소에는 끄고, 귓속말이 인식되지 않을 때만 켜서 확인한다.
	 */
	public boolean debugLog = false;
	/** 설정 파일 형식 번호. 기본값이 바뀐 항목을 한 번만 고쳐 주는 데 쓴다. */
	public Integer configVersion;

	/** 지금 쓰는 테마 id: 미리 만든 테마(dark, pink, kakao) 또는 사용자가 만든 테마(custom_1 ...) */
	public String themePreset = "dark";
	/** 사용자가 만든 테마들 */
	public List<Theme> customThemes = new ArrayList<>();
	/** 1.5.0 의 "직접 설정" 테마. 읽을 때 customThemes 로 옮긴다. */
	public Theme customTheme = null;

	/** 테마와 상관없는 크기 값 */
	public static class Sizes {
		/** 말풍선 글자 배율 */
		public float textScale = 1.0f;
		public int bubblePadding = 4;
		public int messageGap = 4;
		/** 이모티콘 크기 (GUI 픽셀) */
		public int stickerSize = 48;
		/** 채팅창(T)이 닫혀 있을 때 팝업 불투명도 (10~100) */
		public int popupOpacity = 60;
		/** 메인 창과 팝업 모서리 둥글기 (0 이면 각진 모서리) */
		public int cornerRadius = 0;

		// 영역별 글자 크기 배율 (1 = 기본). 기본 크기는 GUI 배율에 맞춰 정해지고 여기에 곱한다.
		/** 목록: 대화 상대/채널 이름 */
		public float listNameScale = 1.0f;
		/** 목록: 미리보기, 시간, 안 읽음 배지, 구역 이름 */
		public float listSubScale = 1.0f;
		/** 대화창 머리글의 대화 상대 이름 */
		public float titleScale = 1.0f;
		/** 대화창: 말풍선 옆 시간, 날짜, 말풍선 위 이름표, 서버 안내 */
		public float metaScale = 1.0f;
		/** 팝업: 제목줄, 탭, 대화 내용 전체 */
		public float popupScale = 1.0f;
	}

	public Sizes sizes = new Sizes();
	/** 모드 창/팝업 글꼴: default, uniform(유니코드), 또는 등록한 글꼴 id */
	public String font = "default";
	/** 새 버전 안내용 주소. {"version":"1.6.0","download":"https://...","message":"..."} 형태의 JSON. 비워두면 확인하지 않는다. */
	public String updateUrl = "";

	/** 팝업 창 하나. 위치와 크기는 화면 좌표(GUI 배율 기준) */
	public static class PopupWindow {
		/** 열어둔 대화들(탭)과 지금 보고 있는 대화 */
		public List<String> tabs = new ArrayList<>();
		public String active;
		public int x = 8;
		public int y = 40;
		public int width = 190;
		public int height = 130;
	}

	/** 띄워둔 팝업들. 뒤에 있을수록 위에 그려진다. */
	public List<PopupWindow> popups = new ArrayList<>();

	// 1.3.0 까지 쓰던 단일 팝업 설정. 읽을 때 popups 로 옮기고 비운다 (null 이면 파일에 쓰지 않는다).
	public List<String> popupTabs;
	public String popupActive;
	public String popupConversation;
	public Integer popupX, popupY, popupWidth, popupHeight;

	public static ModConfig get() {
		return instance;
	}

	public static ChannelSettings channel(Channel channel) {
		if (instance.channels == null) instance.channels = new LinkedHashMap<>();
		return instance.channels.computeIfAbsent(channel.id, id -> new ChannelSettings(channel));
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
				ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
				if (loaded != null) instance = loaded;
				if (instance.whisperPattern == null || OLD_DEFAULT_PATTERNS.contains(instance.whisperPattern)) {
					instance.whisperPattern = DEFAULT_PATTERN;
				}
				instance.migratePopups();
				instance.migrateThemes();
			} catch (Exception e) {
				ChattingPlusClient.LOGGER.error("Failed to read {}", FILE, e);
			}
		}
		// 2: 진단 로그 기본값을 끔 (예전 버전은 켜 둔 채로 저장돼 있다)
		if (instance.configVersion == null || instance.configVersion < 2) {
			instance.debugLog = false;
			instance.configVersion = 2;
		}
		for (Channel c : Channel.values()) channel(c);
		save(); // 새로 생긴 항목을 파일에 채워 넣는다
	}

	private void migrateThemes() {
		if (customThemes == null) customThemes = new ArrayList<>();
		if (sizes == null) sizes = new Sizes();
		customThemes.removeIf(t -> t == null || t.id == null);
		if (customTheme != null) {
			customTheme.id = "custom_" + (customThemes.size() + 1);
			customTheme.name = "내 테마";
			customThemes.add(customTheme);
			if ("custom".equals(themePreset)) themePreset = customTheme.id;
			customTheme = null;
		}
		if (Theme.byId(themePreset) == null) themePreset = "dark";
	}

	private void migratePopups() {
		if (popups == null) popups = new ArrayList<>();
		List<String> oldTabs = popupTabs != null ? new ArrayList<>(popupTabs) : new ArrayList<>();
		if (oldTabs.isEmpty() && popupConversation != null) oldTabs.add(popupConversation);
		if (popups.isEmpty() && !oldTabs.isEmpty()) {
			PopupWindow window = new PopupWindow();
			window.tabs = oldTabs;
			window.active = popupActive != null ? popupActive : oldTabs.get(0);
			if (popupX != null) window.x = popupX;
			if (popupY != null) window.y = popupY;
			if (popupWidth != null) window.width = popupWidth;
			if (popupHeight != null) window.height = popupHeight;
			popups.add(window);
		}
		popupTabs = null;
		popupActive = popupConversation = null;
		popupX = popupY = popupWidth = popupHeight = null;
		popups.removeIf(w -> w == null || w.tabs == null || w.tabs.isEmpty());
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			ChattingPlusClient.LOGGER.error("Failed to write {}", FILE, e);
		}
	}
}
