package com.chattingplus;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 켜고 끄는 밀어서 바꾸는 스위치. 켜지면 강조색 바탕에 손잡이가 오른쪽, 꺼지면 회색 바탕에 왼쪽.
 * 값은 get 으로 읽어서 그리므로 다른 곳에서 바뀌어도 그대로 따라간다. 누르면 set 에 반대 값을 넘긴다.
 */
public class UiSwitch extends ClickableWidget {
	/** 손잡이가 끝에서 끝까지 미끄러지는 시간 */
	private static final long SLIDE_MS = 120;

	private final BooleanSupplier get;
	private final Consumer<Boolean> set;
	/** 손잡이 위치 0(왼쪽)~1(오른쪽). 값이 바뀌면 부드럽게 따라간다 */
	private float knob;
	private long lastFrame;

	public UiSwitch(int x, int y, int width, int height, Text name, BooleanSupplier get, Consumer<Boolean> set) {
		super(x, y, width, height, name);
		this.get = get;
		this.set = set;
		this.knob = get.getAsBoolean() ? 1 : 0;
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		set.accept(!get.getAsBoolean());
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		Theme theme = Theme.get();
		boolean on = get.getAsBoolean();
		long now = Util.getMeasuringTimeMs();
		float step = lastFrame == 0 ? 1 : Math.min(1, (now - lastFrame) / (float) SLIDE_MS);
		lastFrame = now;
		knob = on ? Math.min(1, knob + step) : Math.max(0, knob - step);

		int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
		int off = Theme.mix(theme.headerBg | 0xFF000000, theme.mutedText, 0.45f);
		int track = Theme.mix(off, theme.selection() | 0xFF000000, knob);
		if (isSelected()) track = Theme.mix(track, theme.text, 0.12f);
		int r = height / 2;
		Shapes.fill(context, x0, y0, x1, y1, track, r);
		// 손잡이: 테두리 안쪽 1픽셀 여백을 둔 동그라미
		int size = height - 2;
		int kx = x0 + 1 + Math.round(knob * (width - 2 - size));
		Shapes.fill(context, kx, y0 + 1, kx + size, y0 + 1 + size, 0xFFFFFFFF, size / 2);
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		appendDefaultNarrations(builder);
	}

	@Override
	public Text getMessage() {
		return super.getMessage().copy().append(": ").append(ScreenTexts.onOrOff(get.getAsBoolean()));
	}
}
