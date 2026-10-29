package com.chattingplus;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** 서버의 채널 채팅 줄: 아이콘 문자들 + [칭호] 닉네임 : 내용. 채널은 아이콘 문자(ꀇ 전체, ꀈ 지역, ꀉ 마을)로 구분 */
class ChannelTest {
	private static final String GLOBAL_ICONS = "󯿻ꀂ󐀁ꀇ󐀂ꀃ ";
	private static final String LOCAL_ICONS = "󯿻ꀂ󐀁ꀈ󐀂ꀃ ";
	private static final String VILLAGE_ICONS = "󯿻ꀂ󯿾ꀁ󯼡󐀁ꀉ󯿿";

	@Test
	void globalLineWithTitle() {
		Channel.Line line = Channel.parse(GLOBAL_ICONS + "[1st] 백령 : 국밥 먹을지 말지");
		assertNotNull(line);
		assertEquals(Channel.GLOBAL, line.channel());
		assertEquals("백령", line.sender());
		assertEquals("국밥 먹을지 말지", line.text());
	}

	@Test
	void localAndVillageLines() {
		Channel.Line local = Channel.parse(LOCAL_ICONS + "[1st] 백령 : 지역 채팅");
		assertNotNull(local);
		assertEquals(Channel.LOCAL, local.channel());

		Channel.Line village = Channel.parse(VILLAGE_ICONS + "[창백마을](이장) 백령 : 마을 채팅");
		assertNotNull(village);
		assertEquals(Channel.VILLAGE, village.channel());
		assertEquals("백령", village.sender());
	}

	@Test
	void nameWithoutTitle() {
		Channel.Line line = Channel.parse(GLOBAL_ICONS.replace(" ", "") + " RhRh_0404 : 경기보다 요리가 먼저입니다.");
		assertNotNull(line);
		assertEquals("RhRh_0404", line.sender());
	}

	@Test
	void otherServerLinesAreNotChannelChat() {
		// 확성기(ꀑ), 시스템 안내, 일반 문장
		assertNull(Channel.parse("󯿻ꀂ󯿿ꀁ󯼡ꀑ󐀂[확성기]󐀃Gogouma0720 : 아8 -627"));
		assertNull(Channel.parse("󯿻ꀂ󯿿ꀁ󯼡𑙥 [플리마켓] 플리마켓의 금고 잔액이 부족합니다."));
		assertNull(Channel.parse("크랙님은 오프라인 상태입니다."));
	}

	@Test
	void labelKeepsTitleAndNameColors() {
		MutableText message = Text.empty()
				.append(Text.literal(GLOBAL_ICONS))
				.append(Text.literal("[설날의 주인공] ").formatted(Formatting.LIGHT_PURPLE))
				.append(Text.literal("뿌릉").withColor(0xFFB347))
				.append(Text.literal(" : 창백숲 필요하신분").formatted(Formatting.WHITE));
		Channel.Line line = Channel.parse(message);
		assertNotNull(line);
		assertEquals("뿌릉", line.sender());
		assertNotNull(line.label());

		// 이름표 = 채널 아이콘 다음부터 " : " 앞까지, 색 그대로
		String label = line.label().getString();
		assertTrue(label.endsWith("[설날의 주인공] 뿌릉"), label);
		assertEquals(TextColor.fromFormatting(Formatting.LIGHT_PURPLE), styleOf(line.label(), "[설날의 주인공]").getColor());
		assertEquals(TextColor.fromRgb(0xFFB347), styleOf(line.label(), "뿌릉").getColor());
	}

	/** 이름표에서 글자 조각 part 가 들어 있는 부분의 꾸밈 */
	private static Style styleOf(Text text, String part) {
		List<Style> found = new ArrayList<>();
		text.visit((style, s) -> {
			if (s.contains(part)) found.add(style);
			return Optional.empty();
		}, Style.EMPTY);
		assertFalse(found.isEmpty(), "no segment with " + part);
		return found.get(0);
	}
}
