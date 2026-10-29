package com.chattingplus;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 채팅 한 줄을 글자 단위로 펼쳐서, 글자마다 원래 꾸밈(색, 서버 리소스팩 글꼴 등)을 기억한다.
 * 줄을 문자열로 해석한 뒤, 원하는 부분(예: 등급 아이콘 + 칭호 + 닉네임)만 원래 모양 그대로 다시 잘라 낼 수 있다.
 * 문자열 안의 옛날식 색 코드(§x)는 해석에 방해되므로 지운다.
 */
final class StyledLine {
	private final String plain;
	private final List<Style> styles;

	private StyledLine(String plain, List<Style> styles) {
		this.plain = plain;
		this.styles = styles;
	}

	static StyledLine of(Text text) {
		StringBuilder sb = new StringBuilder();
		List<Style> styles = new ArrayList<>();
		text.visit((style, part) -> {
			for (int i = 0; i < part.length(); i++) {
				char ch = part.charAt(i);
				if (ch == '§' && i + 1 < part.length()) {
					i++;
					continue;
				}
				sb.append(ch);
				styles.add(style);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return new StyledLine(sb.toString(), styles);
	}

	static StyledLine of(String raw) {
		return of(Text.literal(raw));
	}

	String plain() {
		return plain;
	}

	/**
	 * [from, to) 부분을 원래 꾸밈으로 다시 만든다. 앞뒤 공백은 뺀다.
	 * 누르기/가리키기 동작은 모드 화면에서 쓰지 않으므로 지운다 (저장하기도 쉬워진다).
	 */
	Text slice(int from, int to) {
		while (from < to && plain.charAt(from) == ' ') from++;
		while (to > from && plain.charAt(to - 1) == ' ') to--;
		MutableText out = Text.empty();
		int i = from;
		while (i < to) {
			Style style = styles.get(i);
			int j = i + 1;
			while (j < to && styles.get(j).equals(style)) j++;
			out.append(Text.literal(plain.substring(i, j)).setStyle(style.withClickEvent(null).withHoverEvent(null).withInsertion(null)));
			i = j;
		}
		return out;
	}
}
