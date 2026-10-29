package com.chattingplus;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 팝업 전용 키(기본 Y)로 여는 빈 화면. 바닐라 채팅 입력창 없이 팝업만 조작한다.
 * 글자를 입력하거나 마우스를 쓰려면 화면이 하나 열려 있어야 해서 만든 것으로, 그리는 것은 없고
 * 팝업의 입력칸과 마우스 처리는 채팅창(T)을 열 때처럼 PopupChat 이 붙인다. Esc 로 닫는다.
 */
public class PopupScreen extends Screen {
	public PopupScreen() {
		super(Text.translatable("key.chattingplus.popup"));
	}

	/** 게임 화면을 어둡게 하거나 흐리지 않는다 */
	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
