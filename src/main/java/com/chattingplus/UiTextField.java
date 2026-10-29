package com.chattingplus;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** 테마 색을 따르는 입력칸. 테마에서 "마인크래프트 스타일"을 켜면 원래 입력칸으로 그린다. */
public class UiTextField extends TextFieldWidget {
	private static final int VANILLA_TEXT = 0xE0E0E0;
	private static final int VANILLA_UNEDITABLE = 0x707070;

	public UiTextField(TextRenderer textRenderer, int x, int y, int width, int height, Text narration) {
		super(textRenderer, x, y, width, height, narration);
	}

	/** 테마 모양이고 테마가 글자 그림자를 끄면 그림자 없이 그린다 (mixin 에서 확인) */
	public boolean plainText() {
		Theme theme = Theme.get();
		return !theme.vanillaWidgets && !theme.textShadow;
	}

	@Override
	public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		Theme theme = Theme.get();
		if (theme.vanillaWidgets) {
			setEditableColor(VANILLA_TEXT);
			setUneditableColor(VANILLA_UNEDITABLE);
			super.renderWidget(context, mouseX, mouseY, delta);
			return;
		}
		int x = getX(), y = getY(), w = width;
		int border = isFocused() ? theme.selection() | 0xFF000000 : theme.divider | 0xFF000000;
		int fill = Theme.mix(theme.chatBg | 0xFF000000, theme.text, 0.06f);
		UiButton.fillRounded(context, x, y, x + w, y + height, border);
		UiButton.fillRounded(context, x + 1, y + 1, x + w - 1, y + height - 1, fill);
		setEditableColor(theme.text);
		setUneditableColor(theme.mutedText);

		// 원래 배경 없이 글자만 그리게 하고, 배경이 있을 때와 같은 자리에 오도록 잠깐 옮긴다
		setDrawsBackground(false);
		setX(x + 4);
		setY(y + (height - 8) / 2);
		setWidth(w - 8);
		super.renderWidget(context, mouseX, mouseY, delta);
		setX(x);
		setY(y);
		setWidth(w);
		setDrawsBackground(true);
	}
}
