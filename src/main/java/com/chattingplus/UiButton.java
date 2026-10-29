package com.chattingplus;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 테마 색을 따르는 둥근 평면 버튼. 테마에서 "마인크래프트 스타일"을 켜면 원래 버튼 그림으로 그린다.
 * 보조 버튼(팝업, 삭제 같은)은 small() 로 글자를 한 단계 작게.
 * 만드는 법은 ButtonWidget.builder 와 같다: UiButton.create(글자, 동작).dimensions(...).tooltip(...).build()
 */
public class UiButton extends ButtonWidget {
	private static final Identifier VANILLA = Identifier.ofVanilla("widget/button");
	private static final Identifier VANILLA_DISABLED = Identifier.ofVanilla("widget/button_disabled");
	private static final Identifier VANILLA_HIGHLIGHTED = Identifier.ofVanilla("widget/button_highlighted");

	/** 아이콘 그림 원본 크기 */
	private static final int ICON_TEXTURE = 64;

	/** 음수면 보조 버튼: 그릴 때 GUI 배율에 맞는 작은 글자 */
	private final float textScale;
	/** 있으면 글자 대신 아이콘을 그린다 (글자는 화면 낭독용으로만) */
	private final Identifier icon;

	protected UiButton(int x, int y, int width, int height, Text message, PressAction onPress, float textScale, Identifier icon) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
		this.textScale = textScale;
		this.icon = icon;
	}

	/** textures/gui/icon/{name}.png (흰색 그림, 그릴 때 색을 입힌다) */
	public static Identifier icon(String name) {
		return Identifier.of(ChattingPlusClient.MOD_ID, "textures/gui/icon/" + name + ".png");
	}

	/** 아이콘을 칸 가운데에 색을 입혀 그린다 */
	public static void drawIcon(DrawContext context, Identifier icon, int x, int y, int w, int h, int color) {
		int size = Math.max(8, Math.min(w, h) - 4);
		context.drawTexture(SmoothTextures::layer, icon, x + (w - size) / 2, y + (h - size) / 2, 0, 0, size, size,
				ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE, color);
	}

	private void drawContent(DrawContext context, int color, boolean shadow) {
		if (icon != null) drawIcon(context, icon, getX(), getY(), width, height, color);
		else drawLabel(context, this, getMessage(), color, scale(), shadow);
	}

	public static Builder create(Text message, PressAction onPress) {
		return new Builder(message, onPress);
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		Theme theme = Theme.get();
		if (theme.vanillaWidgets) {
			Identifier sprite = !active ? VANILLA_DISABLED : isSelected() ? VANILLA_HIGHLIGHTED : VANILLA;
			context.drawGuiTexture(RenderLayer::getGuiTextured, sprite, getX(), getY(), width, height);
			drawContent(context, active ? 0xFFFFFFFF : 0xFFA0A0A0, true);
			return;
		}
		// 머리글 위에 놓여도 구분되도록 머리글 색에 글자색을 살짝 섞는다
		int base = Theme.mix(theme.headerBg | 0xFF000000, theme.text, 0.12f);
		int bg = !active ? Theme.mix(base, theme.windowBg | 0xFF000000, 0.5f)
				: isSelected() ? Theme.mix(base, theme.selection(), 0.3f) : base;
		fillRounded(context, getX(), getY(), getX() + width, getY() + height, bg);
		drawContent(context, (active ? theme.text : theme.mutedText) | 0xFF000000, theme.textShadow);
	}

	private float scale() {
		return textScale < 0 ? UiFont.small() : textScale;
	}

	/** 가운데 정렬 글자 (넘치면 잘라서) */
	static void drawLabel(DrawContext context, ClickableWidget widget, Text message, int color, float scale, boolean shadow) {
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		int textW = Math.round(tr.getWidth(message) * scale);
		int x = widget.getX() + (widget.getWidth() - textW) / 2;
		int y = widget.getY() + (widget.getHeight() - UiFont.height(scale)) / 2;
		context.enableScissor(widget.getX() + 2, widget.getY(), widget.getX() + widget.getWidth() - 2, widget.getY() + widget.getHeight());
		UiFont.drawText(context, tr, message, Math.max(widget.getX() + 2, x), y, color, scale, shadow);
		context.disableScissor();
	}

	/** 모서리를 1픽셀씩 깎은 사각형 */
	public static void fillRounded(DrawContext context, int x0, int y0, int x1, int y1, int color) {
		context.fill(x0 + 1, y0, x1 - 1, y1, color);
		context.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
		context.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
	}

	public static final class Builder {
		private final Text message;
		private final PressAction onPress;
		private int x, y, width = 150, height = 20;
		private Tooltip tooltip;
		private float textScale = UiFont.NORMAL;
		private Identifier icon;

		private Builder(Text message, PressAction onPress) {
			this.message = message;
			this.onPress = onPress;
		}

		public Builder dimensions(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			return this;
		}

		public Builder tooltip(Tooltip tooltip) {
			this.tooltip = tooltip;
			return this;
		}

		/** 보조 버튼: 글자를 한 단계 작게 */
		public Builder small() {
			this.textScale = -1; // 그릴 때 UiFont.small()
			return this;
		}

		/** 글자 대신 아이콘 (textures/gui/icon/{name}.png) */
		public Builder icon(String name) {
			this.icon = UiButton.icon(name);
			return this;
		}

		public UiButton build() {
			UiButton button = new UiButton(x, y, width, height, message, onPress, textScale, icon);
			if (tooltip != null) button.setTooltip(tooltip);
			return button;
		}
	}
}
