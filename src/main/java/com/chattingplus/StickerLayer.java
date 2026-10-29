package com.chattingplus;

import com.chattingplus.ModConfig.Sticker;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 스티커들을 화면에 그리고, 편집 중이면 옮기기/크기 조절/떼기/핀 고정을 받는다. 스티커는 화면 어디에나 둘 수 있다.
 * - 스티커를 끌면 옮겨지고 맨 위로 올라온다
 * - 오른쪽 아래 손잡이를 끌면 비율을 지킨 채 크기가 바뀐다
 * - 오른쪽 위 ×나 오른쪽 클릭으로 뗀다
 * - 왼쪽 위 핀(팝업만)을 누르면 팝업에 고정되어 팝업을 옮길 때 따라간다. 다시 누르면 화면 그 자리에 남는다
 * 편집 중이 아니면 마우스를 받지 않는다 (스티커 아래를 그대로 누를 수 있게).
 */
final class StickerLayer {
	private static final int HANDLE = 5;
	private static final int BUTTON = 8;

	/**
	 * 스티커 위치를 계산하는 기준: 화면 크기와, 핀으로 고정한 스티커의 기준점(팝업 왼쪽 위).
	 * pinnable 이 false 면(메인 창) 핀 버튼이 없다.
	 */
	record Frame(int screenW, int screenH, int originX, int originY, boolean pinnable) {}

	private record Rect(int x0, int y0, int x1, int y1) {
		boolean contains(double mx, double my) {
			return mx >= x0 && mx < x1 && my >= y0 && my < y1;
		}
	}

	private enum Mode { NONE, MOVE, RESIZE }

	private Mode mode = Mode.NONE;
	private Sticker active;
	private Frame activeFrame;
	private double grabX, grabY;

	/** 스티커 가운데의 화면 좌표 */
	static double centerX(Sticker s, Frame f) {
		return s.pinned ? f.originX + s.x : s.x * f.screenW;
	}

	static double centerY(Sticker s, Frame f) {
		return s.pinned ? f.originY + s.y : s.y * f.screenH;
	}

	/** 가운데를 화면 좌표 (cx, cy) 로. 화면 밖으로는 못 나간다 */
	private static void setCenter(Sticker s, Frame f, double cx, double cy) {
		cx = Math.max(0, Math.min(f.screenW, cx));
		cy = Math.max(0, Math.min(f.screenH, cy));
		if (s.pinned) {
			s.x = (float) (cx - f.originX);
			s.y = (float) (cy - f.originY);
		} else {
			s.x = (float) (cx / Math.max(1, f.screenW));
			s.y = (float) (cy / Math.max(1, f.screenH));
		}
	}

	/** 핀 고정/해제. 화면에서 보이는 자리는 그대로 두고 위치의 기준만 바꾼다 */
	static void togglePin(Sticker s, Frame f) {
		double cx = centerX(s, f), cy = centerY(s, f);
		s.pinned = !s.pinned;
		setCenter(s, f, cx, cy);
	}

	/** 가로·세로 크기: 긴 변이 size, 짧은 변은 그림 비율대로 */
	private static int[] dims(Sticker s) {
		float aspect = Stickers.aspect(s.source);
		return aspect >= 1 ? new int[]{s.size, Math.max(1, Math.round(s.size / aspect))}
				: new int[]{Math.max(1, Math.round(s.size * aspect)), s.size};
	}

	private static Rect rect(Sticker s, Frame f) {
		int[] d = dims(s);
		int cx = (int) Math.round(centerX(s, f)), cy = (int) Math.round(centerY(s, f));
		return new Rect(cx - d[0] / 2, cy - d[1] / 2, cx - d[0] / 2 + d[0], cy - d[1] / 2 + d[1]);
	}

	private static Rect handle(Rect r) {
		return new Rect(r.x1 - HANDLE, r.y1 - HANDLE, r.x1, r.y1);
	}

	private static Rect deleteButton(Rect r) {
		return new Rect(r.x1 - BUTTON, r.y0, r.x1, r.y0 + BUTTON);
	}

	private static Rect pinButton(Rect r) {
		return new Rect(r.x0, r.y0, r.x0 + BUTTON, r.y0 + BUTTON);
	}

	boolean dragging() {
		return mode != Mode.NONE;
	}

	/** alpha 는 흐려진 팝업일 때 */
	void render(DrawContext context, List<Sticker> stickers, Frame frame, boolean editing, int mouseX, int mouseY, float alpha) {
		if (stickers.isEmpty()) return;
		Theme theme = Theme.get();
		int color = Theme.withAlpha(0xFFFFFF, Math.round(255 * alpha));
		Sticker hovered = editing && mode == Mode.NONE ? stickerAt(stickers, frame, mouseX, mouseY) : active;
		for (Sticker s : stickers) {
			Rect r = rect(s, frame);
			boolean drawn = Stickers.draw(context, s.source, r.x0, r.y0, r.x1 - r.x0, r.y1 - r.y0, color);
			if (!editing) continue;
			// 편집 중: 모든 스티커에 옅은 테두리(고정한 스티커는 강조색), 가리킨 스티커에는 손잡이, ×, 핀
			boolean on = s == hovered;
			int accent = theme.selection() | 0xFF000000;
			int border = on || (frame.pinnable && s.pinned) ? accent : Theme.withAlpha(theme.mutedText, drawn ? 0x80 : 0xFF);
			context.drawBorder(r.x0, r.y0, r.x1 - r.x0, r.y1 - r.y0, border);
			if (frame.pinnable && s.pinned && !on) {
				// 고정한 스티커는 가리키지 않아도 작은 핀 표시
				Rect p = pinButton(r);
				context.fill(p.x0, p.y0, p.x1, p.y1, accent);
				UiButton.drawIcon(context, UiButton.icon("pin"), p.x0, p.y0, BUTTON, BUTTON, 0xFFFFFFFF);
			}
			if (!on) continue;
			Rect h = handle(r);
			context.fill(h.x0, h.y0, h.x1, h.y1, accent);
			Rect d = deleteButton(r);
			context.fill(d.x0, d.y0, d.x1, d.y1, theme.accent | 0xFF000000);
			UiButton.drawIcon(context, UiButton.icon("close"), d.x0, d.y0, BUTTON, BUTTON, 0xFFFFFFFF);
			if (frame.pinnable) {
				Rect p = pinButton(r);
				context.fill(p.x0, p.y0, p.x1, p.y1, s.pinned ? accent : Theme.mix(theme.headerBg | 0xFF000000, theme.text, 0.2f));
				UiButton.drawIcon(context, UiButton.icon("pin"), p.x0, p.y0, BUTTON, BUTTON, s.pinned ? 0xFFFFFFFF : theme.text | 0xFF000000);
			}
		}
	}

	private static Sticker stickerAt(List<Sticker> stickers, Frame frame, double mx, double my) {
		for (int i = stickers.size() - 1; i >= 0; i--) {
			if (rect(stickers.get(i), frame).contains(mx, my)) return stickers.get(i);
		}
		return null;
	}

	/** 편집 중 클릭. 스티커를 눌렀으면 true (다른 곳은 평소처럼 동작하게 false) */
	boolean press(List<Sticker> stickers, Frame frame, double mx, double my, int button) {
		Sticker s = stickerAt(stickers, frame, mx, my);
		if (s == null) return false;
		Rect r = rect(s, frame);
		boolean left = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || (left && deleteButton(r).contains(mx, my))) {
			stickers.remove(s);
			ModConfig.save();
			return true;
		}
		if (!left) return true;
		if (frame.pinnable && pinButton(r).contains(mx, my)) {
			togglePin(s, frame);
			ModConfig.save();
			return true;
		}
		// 누른 스티커는 맨 위로
		stickers.remove(s);
		stickers.add(s);
		active = s;
		activeFrame = frame;
		if (handle(r).contains(mx, my)) {
			mode = Mode.RESIZE;
		} else {
			mode = Mode.MOVE;
			grabX = mx - centerX(s, frame);
			grabY = my - centerY(s, frame);
		}
		return true;
	}

	void drag(double mx, double my) {
		if (mode == Mode.NONE || active == null) return;
		Frame f = activeFrame;
		if (mode == Mode.MOVE) {
			setCenter(active, f, mx - grabX, my - grabY);
		} else {
			// 손잡이까지의 가로·세로 거리를 긴 변 길이로 바꿔서 큰 쪽을 쓴다 (비율 유지)
			float aspect = Stickers.aspect(active.source);
			double halfW = mx - centerX(active, f), halfH = my - centerY(active, f);
			double fromW = aspect >= 1 ? 2 * halfW : 2 * halfW / aspect;
			double fromH = aspect >= 1 ? 2 * halfH * aspect : 2 * halfH;
			int size = (int) Math.round(Math.max(fromW, fromH));
			active.size = Math.max(Sticker.MIN_SIZE, Math.min(Sticker.MAX_SIZE, size));
		}
	}

	/** 끌기를 끝내고 저장 */
	void release() {
		if (mode == Mode.NONE) return;
		mode = Mode.NONE;
		active = null;
		activeFrame = null;
		ModConfig.save();
	}
}
