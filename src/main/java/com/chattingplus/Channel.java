package com.chattingplus;

import net.minecraft.text.Text;

/**
 * 서버의 공개 채팅 채널. 이 서버는 채팅 줄 앞에 채널마다 다른 아이콘 문자를 붙이므로 그것으로 구분한다.
 * 예: 󏿻ꀂ󐀁ꀇ󐀂ꀃ [1st] 백령 : 안녕  (ꀇ = 전체)
 */
public enum Channel {
	GLOBAL("global", "전체", "ㅈ", "ꀇ"),
	LOCAL("local", "지역", "ㅇ", "ꀈ"),
	VILLAGE("village", "마을", "ㅁ", "ꀉ"),
	/** 채널 채팅과 귓속말이 아닌 나머지 모든 메시지 (서버 안내, 명령어 결과, 일반 채팅 등). 입력은 바닐라 채팅처럼 보낸다 */
	SYSTEM("system", "시스템", "", "");

	/**
	 * 채널 채팅 한 줄.
	 * label: 채널 아이콘과 " : " 사이를 원래 모양 그대로 (등급 아이콘, 칭호, 색이 있는 닉네임). 모르면 null
	 */
	public record Line(Channel channel, String sender, String text, Text label) {
		public Line(Channel channel, String sender, String text) {
			this(channel, sender, text, null);
		}
	}

	public final String id;
	public final String label;
	final String defaultCommand;
	final String defaultMarker;

	Channel(String id, String label, String defaultCommand, String defaultMarker) {
		this.id = id;
		this.label = label;
		this.defaultCommand = defaultCommand;
		this.defaultMarker = defaultMarker;
	}

	/** WhisperStore 와 팝업 탭에서 쓰는 이름. 마인크래프트 닉네임은 #으로 시작할 수 없어서 겹치지 않는다. */
	public String key() {
		return "#" + id;
	}

	public ModConfig.ChannelSettings settings() {
		return ModConfig.channel(this);
	}

	public static Channel fromKey(String name) {
		if (name == null || !name.startsWith("#")) return null;
		for (Channel c : values()) if (c.key().equalsIgnoreCase(name)) return c;
		return null;
	}

	/** 채널 채팅이 아니면 null */
	public static Line parse(String raw) {
		return parse(StyledLine.of(raw));
	}

	/** 채널 채팅이 아니면 null. 원래 꾸밈(등급, 닉네임 색)도 같이 읽는다 */
	public static Line parse(Text message) {
		return parse(StyledLine.of(message));
	}

	private static Line parse(StyledLine styled) {
		String line = styled.plain();
		// 줄 앞의 아이콘 문자 묶음 (한글, 영문, 숫자, 괄호가 나오기 전까지)
		int start = 0;
		while (start < line.length()) {
			char ch = line.charAt(start);
			if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HANGUL
					|| (ch < 128 && Character.isLetterOrDigit(ch)) || ch == '[' || ch == '(') break;
			start++;
		}
		String icons = line.substring(0, start);

		Channel channel = null;
		int labelStart = 0;
		for (Channel c : values()) {
			String marker = c.settings().marker;
			int at = marker == null || marker.isEmpty() ? -1 : icons.indexOf(marker);
			if (at >= 0) {
				channel = c;
				labelStart = at + marker.length();
				break;
			}
		}
		if (channel == null) return null;

		String rest = line.substring(start);
		int colon = rest.indexOf(" : ");
		if (colon <= 0) return null;
		// "[창백마을](이장) 백령" 처럼 칭호가 붙어 있으면 마지막 단어만
		String sender = WhisperParser.cleanName(rest.substring(0, colon));
		if (sender.isEmpty()) return null;
		// 채널 아이콘 다음부터 " : " 앞까지: 등급 아이콘, 칭호, 닉네임을 서버가 보낸 모양 그대로
		Text label = styled.slice(labelStart, start + colon);
		return new Line(channel, sender, rest.substring(colon + 3).trim(), label);
	}
}
