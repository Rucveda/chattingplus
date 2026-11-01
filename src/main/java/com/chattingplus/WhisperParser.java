package com.chattingplus;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** 채팅창 문자열에서 귓속말을 찾아낸다. */
public final class WhisperParser {
	public record Whisper(String partner, boolean outgoing, String text) {}

	// 채팅 한 줄마다 여러 번 쓰므로 미리 만들어 둔다
	private static final Pattern COLOR_CODE = Pattern.compile("§.");
	private static final Pattern LEADING_ICONS = Pattern.compile("^[^\\p{IsHangul}A-Za-z0-9\\[(]*");

	private static String compiledSource;
	private static Pattern compiled;

	private WhisperParser() {}

	/** 귓속말이 아니면 null */
	public static Whisper parse(String raw) {
		String line = stripColors(raw).trim();
		Matcher m = pattern().matcher(line);
		if (!m.matches()) return null;

		boolean outgoing = m.group("dir").equals(ModConfig.get().sentTag);
		// 내가 보냈으면 받는 사람이, 받았으면 보낸 사람이 대화 상대
		String partner = cleanName(outgoing ? m.group("to") : m.group("from"));
		if (partner.isEmpty()) return null;
		return new Whisper(partner, outgoing, m.group("msg").trim());
	}

	/**
	 * 다른 모드가 귓속말/채널 채팅으로 주고받는 데이터 문자열이면 true.
	 * Together Plus 는 "~tp" 로 시작하는 줄로 통신하고 바닐라 채팅창에서는 스스로 지우지만, 우리 기록까지는 지우지 못한다.
	 */
	public static boolean isModData(String text) {
		return stripColors(text).stripLeading().startsWith("~tp");
	}

	/** 서버가 줄 앞에 붙이는 아이콘 문자와 색 코드를 지운다 */
	static String stripIcons(String line) {
		return LEADING_ICONS.matcher(stripColors(line)).replaceFirst("").trim();
	}

	static String stripColors(String text) {
		return text.indexOf('§') < 0 ? text : COLOR_CODE.matcher(text).replaceAll("");
	}

	/** "[VIP] Steve" 처럼 칭호가 붙어 있으면 마지막 단어(닉네임)만 남긴다. */
	static String cleanName(String name) {
		String trimmed = name.trim();
		int space = trimmed.lastIndexOf(' ');
		return space >= 0 ? trimmed.substring(space + 1) : trimmed;
	}

	private static Pattern pattern() {
		String source = ModConfig.get().whisperPattern;
		if (!source.equals(compiledSource)) {
			compiledSource = source;
			try {
				compiled = Pattern.compile(source);
			} catch (PatternSyntaxException e) {
				ChattingPlusClient.LOGGER.error("Invalid whisperPattern in config, using default", e);
				compiled = Pattern.compile(ModConfig.DEFAULT_PATTERN);
			}
		}
		return compiled;
	}
}
