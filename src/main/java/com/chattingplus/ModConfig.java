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

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
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
	/** 메인 창(U) 크기 (GUI 픽셀). 오른쪽 아래 모서리를 끌어 바꾼다. 화면보다 크면 화면에 맞춘다 */
	public int mainWidth = 520;
	public int mainHeight = 320;
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

	/** 지금 쓰는 테마 id: 미리 만든 테마(dark, pink, messenger, dm) 또는 사용자가 만든 테마(custom_1 ...) */
	public String themePreset = "dark";
	/** 사용자가 만든 테마들 */
	public List<Theme> customThemes = new ArrayList<>();

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
	/**
	 * 대화 영역 배경 그림 설정. 설정 화면에서 정하는 기본값이 여기 있고,
	 * 채널 대화와 즐겨찾기한 귓속말은 대화마다 따로 정할 수 있다 ({@link WhisperStore.Conversation#background}).
	 */
	public static class Background {
		/** config/chattingplus/backgrounds 안의 파일 이름. 비우면 없음 */
		public String image = "";
		/** 진하기 (10~100%). 테마 바탕색 위에 이만큼 겹친다 */
		public int opacity = 70;
		/** 크기 (%). 100 이면 대화 영역을 꽉 채우는 크기, 작게 하면 그림 전체가 보인다 (30~300) */
		public int zoom = 100;
		/** 위치 (%). 0 은 왼쪽/위 끝, 50 은 가운데, 100 은 오른쪽/아래 끝에 맞춘다 */
		public int x = 50;
		public int y = 50;

		public Background copy() {
			Background b = new Background();
			b.image = image;
			b.opacity = opacity;
			b.zoom = zoom;
			b.x = x;
			b.y = y;
			return b;
		}

		/** 손으로 고친 파일 등에서 범위를 벗어난 값을 맞춘다 */
		public void sanitize() {
			if (image == null) image = "";
			opacity = Math.max(10, Math.min(100, opacity));
			zoom = Math.max(30, Math.min(300, zoom));
			x = Math.max(0, Math.min(100, x));
			y = Math.max(0, Math.min(100, y));
		}
	}

	/** 기본 배경 그림 */
	public Background background = new Background();
	/** 배경 그림을 메인 창 / 팝업에 쓸지 (대화마다 정한 배경에도 적용) */
	public boolean backgroundMain = true;
	public boolean backgroundPopup = true;
	/** 서버에 처음 들어갈 때 GitHub 에서 새 버전이 나왔는지 확인한다 */
	public boolean checkUpdates = true;

	/** 팝업 창 하나. 위치와 크기는 화면 좌표(GUI 배율 기준) */
	public static class PopupWindow {
		/** 열어둔 대화들(탭)과 지금 보고 있는 대화 */
		public List<String> tabs = new ArrayList<>();
		public String active;
		public int x = 8;
		public int y = 40;
		public int width = 190;
		public int height = 130;
		/** 이 팝업에 붙인 스티커 */
		public List<Sticker> stickers = new ArrayList<>();
	}

	/**
	 * 화면에 붙인 스티커 하나. source: "emote:{이모티콘 글자}" 또는 "file:{stickers 폴더 안 파일 이름}"
	 * 위치(스티커 가운데)는 pinned 에 따라 뜻이 다르다:
	 * - 고정 안 함: 화면 크기에 대한 비율(0~1). 창 크기나 GUI 배율이 바뀌어도 화면의 같은 자리에 있다
	 * - 핀으로 고정(팝업만): 팝업 왼쪽 위에서 떨어진 거리(GUI 픽셀). 팝업을 옮기면 따라간다
	 */
	public static class Sticker {
		public static final int MIN_SIZE = 12;
		public static final int MAX_SIZE = 200;
		/** 고정한 스티커가 팝업에서 떨어질 수 있는 거리 한도 (설정 파일을 손으로 고쳤을 때) */
		public static final int MAX_OFFSET = 4000;

		public String source;
		public float x = 0.5f;
		public float y = 0.5f;
		public boolean pinned;
		/** 긴 변 길이 (GUI 픽셀) */
		public int size = 48;

		public Sticker() {}

		public Sticker(String source, int size) {
			this.source = source;
			this.size = size;
		}
	}

	/** 메인 창(U)에 붙인 스티커 */
	public List<Sticker> mainStickers = new ArrayList<>();

	/** 띄워둔 팝업들. 뒤에 있을수록 위에 그려진다. */
	public List<PopupWindow> popups = new ArrayList<>();

	public static ModConfig get() {
		return instance;
	}

	public static ChannelSettings channel(Channel channel) {
		if (instance.channels == null) instance.channels = new LinkedHashMap<>();
		return instance.channels.computeIfAbsent(channel.id, id -> new ChannelSettings(channel));
	}

	/** 설정 파일 위치. 게임 밖(단위 테스트)에서는 null 이라 읽고 쓰지 않는다. */
	private static Path file() {
		if (Boolean.getBoolean("chattingplus.test")) return null;
		try {
			return FabricLoader.getInstance().getConfigDir().resolve("chattingplus.json");
		} catch (RuntimeException e) {
			return null;
		}
	}

	public static void load() {
		Path file = file();
		if (file != null && Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
				if (loaded != null) instance = loaded;
			} catch (Exception e) {
				ChattingPlusClient.LOGGER.error("Failed to read {}", file, e);
				// 아래에서 기본값으로 덮어쓰기 전에 읽지 못한 파일을 남겨 둔다 (손으로 고치다 잘못된 경우 되살릴 수 있게)
				try {
					Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException copyError) {
					ChattingPlusClient.LOGGER.error("Failed to back up {}", file, copyError);
				}
			}
		}
		instance.sanitize();
		for (Channel c : Channel.values()) channel(c);
		save(); // 새로 생긴 항목을 파일에 채워 넣는다
	}

	/** 손으로 고친 파일에서 빠지거나 잘못된 값을 기본값으로 */
	private void sanitize() {
		if (whisperPattern == null || whisperPattern.isBlank()) whisperPattern = DEFAULT_PATTERN;
		if (sizes == null) sizes = new Sizes();
		if (customThemes == null) customThemes = new ArrayList<>();
		customThemes.removeIf(t -> t == null || t.id == null);
		if (Theme.byId(themePreset) == null) themePreset = "dark";
		if (popups == null) popups = new ArrayList<>();
		popups.removeIf(w -> w == null || w.tabs == null || w.tabs.isEmpty());
		// 메인 창 스티커는 늘 화면 기준 (핀 없음)
		if (mainStickers != null) for (Sticker s : mainStickers) if (s != null) s.pinned = false;
		mainStickers = sanitize(mainStickers);
		for (PopupWindow w : popups) w.stickers = sanitize(w.stickers);
		if (background == null) background = new Background();
		background.sanitize();
	}

	private static List<Sticker> sanitize(List<Sticker> stickers) {
		List<Sticker> list = stickers == null ? new ArrayList<>() : stickers;
		list.removeIf(s -> s == null || s.source == null || s.source.isBlank() || Float.isNaN(s.x) || Float.isNaN(s.y));
		for (Sticker s : list) {
			float lo = s.pinned ? -Sticker.MAX_OFFSET : 0, hi = s.pinned ? Sticker.MAX_OFFSET : 1;
			s.x = Math.max(lo, Math.min(hi, s.x));
			s.y = Math.max(lo, Math.min(hi, s.y));
			s.size = Math.max(Sticker.MIN_SIZE, Math.min(Sticker.MAX_SIZE, s.size));
		}
		return list;
	}

	public static void save() {
		Path file = file();
		if (file == null) return;
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			ChattingPlusClient.LOGGER.error("Failed to write {}", file, e);
		}
	}
}
