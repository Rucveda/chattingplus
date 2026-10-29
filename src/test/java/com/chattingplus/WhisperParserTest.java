package com.chattingplus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 서버의 귓속말 줄 형식: (보낸 사람 → 받는 사람<보냄|받음>) 내용 */
class WhisperParserTest {
	@Test
	void sentWhisperPartnerIsRecipient() {
		WhisperParser.Whisper w = WhisperParser.parse("(백령 → 크랙<보냄>) 안녕 반가워");
		assertNotNull(w);
		assertTrue(w.outgoing());
		assertEquals("크랙", w.partner());
		assertEquals("안녕 반가워", w.text());
	}

	@Test
	void receivedWhisperPartnerIsSender() {
		WhisperParser.Whisper w = WhisperParser.parse("(Alex_12 → 백령<받음>) 거래 할래요? (가격 -> 10)");
		assertNotNull(w);
		assertFalse(w.outgoing());
		assertEquals("Alex_12", w.partner());
		assertEquals("거래 할래요? (가격 -> 10)", w.text());
	}

	@Test
	void serverIconPrefixAndColorCodesAreIgnored() {
		WhisperParser.Whisper w = WhisperParser.parse("󯿻ꀂ §e(늘해랑 → 백령<받음>)§r hi");
		assertNotNull(w);
		assertEquals("늘해랑", w.partner());
		assertEquals("hi", w.text());
	}

	@Test
	void titleBeforeNameIsRemoved() {
		WhisperParser.Whisper w = WhisperParser.parse("([VIP] Bob → 백령<받음>) hi");
		assertNotNull(w);
		assertEquals("Bob", w.partner());
	}

	@Test
	void asciiArrowWorks() {
		WhisperParser.Whisper w = WhisperParser.parse("(백령 -> Kim<보냄>) test");
		assertNotNull(w);
		assertEquals("Kim", w.partner());
	}

	@Test
	void playerChatPretendingToBeWhisperIsRejected() {
		assertNull(WhisperParser.parse("<Steve> (a → b<보냄>) spoof"));
		assertNull(WhisperParser.parse("[플리마켓] (x → y<받음>) fake"));
	}

	@Test
	void ordinaryLinesAreNotWhispers() {
		assertNull(WhisperParser.parse("크랙님은 오프라인 상태입니다."));
		assertNull(WhisperParser.parse("𑙢 마을(으)로 이동을 시작합니다."));
	}

	@Test
	void commandHeadUsesWhisperOrChannelCommand() {
		assertEquals("귓 크랙 ", WhisperSender.commandHead("크랙"));
		assertEquals("ㅈ ", WhisperSender.commandHead(Channel.GLOBAL.key()));
		assertEquals(256 - "귓 크랙 ".length(), WhisperSender.maxTextLength("크랙"));
	}
}
