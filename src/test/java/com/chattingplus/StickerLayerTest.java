package com.chattingplus;

import com.chattingplus.ModConfig.Sticker;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StickerLayerTest {
	/** 400×300 화면, 팝업 왼쪽 위 (100, 50) */
	private static final StickerLayer.Frame FRAME = new StickerLayer.Frame(400, 300, 100, 50, true);

	/** 화면 가운데 (200, 150), 크기 40 → 180~220 */
	private static List<Sticker> one() {
		List<Sticker> list = new ArrayList<>();
		list.add(new Sticker(Stickers.EMOTE + ":웃음:", 40));
		return list;
	}

	@Test
	void dragMovesAnywhereOnScreenButNotOffIt() {
		List<Sticker> list = one();
		StickerLayer layer = new StickerLayer();
		assertTrue(layer.press(list, FRAME, 205, 155, GLFW.GLFW_MOUSE_BUTTON_LEFT));
		layer.drag(305, 35);
		assertEquals(0.75f, list.get(0).x, 0.001f);
		assertEquals(0.1f, list.get(0).y, 0.001f);
		layer.drag(1000, -1000);
		assertEquals(1f, list.get(0).x);
		assertEquals(0f, list.get(0).y);
		layer.release();
		assertFalse(layer.dragging());
	}

	@Test
	void pinnedStickerFollowsPopupAndKeepsItsSpotWhenPinned() {
		List<Sticker> list = one();
		Sticker s = list.get(0);
		StickerLayer.togglePin(s, FRAME);
		// 핀을 꽂아도 화면에서 보이는 자리는 그대로
		assertTrue(s.pinned);
		assertEquals(200, StickerLayer.centerX(s, FRAME), 0.001);
		assertEquals(150, StickerLayer.centerY(s, FRAME), 0.001);
		// 팝업이 (40, 30) 만큼 옮겨지면 같이 옮겨진다
		StickerLayer.Frame moved = new StickerLayer.Frame(400, 300, 140, 80, true);
		assertEquals(240, StickerLayer.centerX(s, moved), 0.001);
		assertEquals(180, StickerLayer.centerY(s, moved), 0.001);
		// 핀을 빼면 그 자리에 남고, 팝업을 옮겨도 그대로
		StickerLayer.togglePin(s, moved);
		assertFalse(s.pinned);
		assertEquals(240, StickerLayer.centerX(s, FRAME), 0.001);
		assertEquals(180, StickerLayer.centerY(s, FRAME), 0.001);
	}

	@Test
	void pinButtonOnlyWherePinnable() {
		StickerLayer layer = new StickerLayer();
		List<Sticker> list = one();
		// 왼쪽 위 핀 자리를 누르면 고정
		assertTrue(layer.press(list, FRAME, 182, 132, GLFW.GLFW_MOUSE_BUTTON_LEFT));
		assertTrue(list.get(0).pinned);
		assertFalse(layer.dragging());
		// 메인 창(핀 없음)에서는 같은 자리가 그냥 옮기기
		List<Sticker> main = one();
		StickerLayer.Frame screen = new StickerLayer.Frame(400, 300, 0, 0, false);
		assertTrue(layer.press(main, screen, 182, 132, GLFW.GLFW_MOUSE_BUTTON_LEFT));
		assertFalse(main.get(0).pinned);
		assertTrue(layer.dragging());
	}

	@Test
	void cornerHandleResizesWithinLimits() {
		List<Sticker> list = one();
		StickerLayer layer = new StickerLayer();
		assertTrue(layer.press(list, FRAME, 219, 169, GLFW.GLFW_MOUSE_BUTTON_LEFT));
		layer.drag(240, 190);
		assertEquals(80, list.get(0).size);
		layer.drag(201, 151);
		assertEquals(Sticker.MIN_SIZE, list.get(0).size);
		layer.drag(2000, 2000);
		assertEquals(Sticker.MAX_SIZE, list.get(0).size);
	}

	@Test
	void rightClickPeelsOffAndEmptySpaceIsIgnored() {
		List<Sticker> list = one();
		StickerLayer layer = new StickerLayer();
		assertFalse(layer.press(list, FRAME, 20, 20, GLFW.GLFW_MOUSE_BUTTON_LEFT));
		assertTrue(layer.press(list, FRAME, 200, 150, GLFW.GLFW_MOUSE_BUTTON_RIGHT));
		assertTrue(list.isEmpty());
	}

	@Test
	void pressedStickerComesToFront() {
		List<Sticker> list = one();
		Sticker bottom = list.get(0);
		Sticker top = new Sticker(Stickers.EMOTE + ":하트:", 40);
		top.x = 0.55f;
		list.add(top);
		StickerLayer layer = new StickerLayer();
		// 겹치지 않는 아래 스티커 왼쪽 부분을 누르면 그 스티커가 맨 위로
		assertTrue(layer.press(list, FRAME, 190, 160, GLFW.GLFW_MOUSE_BUTTON_LEFT));
		assertSame(bottom, list.get(list.size() - 1));
	}

	@Test
	void gifFramesFollowTheirDelays() {
		ImageFiles.Sheet sheet = new ImageFiles.Sheet(null, 10, 10, 2, new int[]{100, 300, 350});
		assertEquals(0, sheet.frameAt(0));
		assertEquals(1, sheet.frameAt(100));
		assertEquals(2, sheet.frameAt(320));
		assertEquals(0, sheet.frameAt(350)); // 처음으로 돌아간다
		assertEquals(12, sheet.u(1));
		assertEquals(12, sheet.v(2));
	}
}
