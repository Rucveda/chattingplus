package com.chattingplus;

import com.chattingplus.WhisperStore.Conversation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatBackgroundTest {
	@Test
	void onlyChannelsAndFavoritesCanHaveTheirOwnBackground() {
		Conversation channel = new Conversation(Channel.GLOBAL.key());
		Conversation whisper = new Conversation("크랙");
		assertTrue(ChatBackground.canCustomize(channel));
		assertFalse(ChatBackground.canCustomize(whisper));
		whisper.favorite = true;
		assertTrue(ChatBackground.canCustomize(whisper));
		assertFalse(ChatBackground.canCustomize(null));
	}

	@Test
	void ownBackgroundIsUsedOnlyWhileAllowed() {
		ModConfig.Background own = new ModConfig.Background();
		own.image = "산.jpg";
		Conversation whisper = new Conversation("크랙");
		whisper.favorite = true;
		whisper.background = own;
		assertSame(own, ChatBackground.of(whisper));
		// 즐겨찾기가 아니면 남아 있어도 기본 배경을 쓴다
		whisper.favorite = false;
		assertSame(ModConfig.get().background, ChatBackground.of(whisper));
		assertSame(ModConfig.get().background, ChatBackground.of(null));
	}

	@Test
	void copyIsIndependentAndSanitizeClamps() {
		ModConfig.Background b = new ModConfig.Background();
		b.zoom = 999;
		b.x = -5;
		b.opacity = 0;
		b.image = null;
		b.sanitize();
		assertEquals(300, b.zoom);
		assertEquals(0, b.x);
		assertEquals(10, b.opacity);
		assertEquals("", b.image);
		ModConfig.Background c = b.copy();
		c.zoom = 50;
		assertEquals(300, b.zoom);
	}

	@Test
	void placeFillsByDefaultAndCanShrinkOrShift() {
		// 넓은 영역(200×100)에 정사각형 그림(50×50): 100% 는 가로를 꽉 채우고 위아래가 잘린다
		assertArrayEquals(new int[]{0, -50, 200, 200}, ChatBackground.place(200, 100, 50, 50, 100, 50, 50));
		// 50% 로 줄이면 높이에 딱 맞아 그림 전체가 보인다. 위치 0% 는 왼쪽에 붙는다
		assertArrayEquals(new int[]{0, 0, 100, 100}, ChatBackground.place(200, 100, 50, 50, 50, 0, 50));
		// 세로 위치 0% 면 그림 맨 위가 보인다
		assertArrayEquals(new int[]{0, 0, 200, 200}, ChatBackground.place(200, 100, 50, 50, 100, 50, 0));
	}
}
