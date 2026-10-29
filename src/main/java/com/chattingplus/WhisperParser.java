package com.chattingplus;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** 채팅창 문자열에서 귓속말을 찾아낸다. */
public final class WhisperParser {
	public record Whisper(String partner, boolean outgoing, String text) {}

	private static String compiledSource;
	private static Pattern compiled;

	private WhisperParser() {}

	/** 귓속말이 아니면 null */
	public static Whisper parse(String raw) {
		String line = raw.replaceAll("§.", "").trim();
		Matcher m = pattern().matcher(line);
		if (!m.matches()) return null;

		boolean outgoing = m.group("dir").equals(ModConfig.get().sentTag);
		// 내가 보냈으면 받는 사람이, 받았으면 보낸 사람이 대화 상대
		String partner = cleanName(outgoing ? m.group("to") : m.group("from"));
		if (partner.isEmpty()) return null;
		return new Whisper(partner, outgoing, m.group("msg").trim());
	}

	/** 서버가 줄 앞에 붙이는 아이콘 문자와 색 코드를 지운다 */
	static String stripIcons(String line) {
		return line.replaceAll("§.", "").replaceFirst("^[^\\p{IsHangul}A-Za-z0-9\\[(]*", "").trim();
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
