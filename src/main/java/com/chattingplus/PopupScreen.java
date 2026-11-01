package com.chattingplus;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * 팝업 전용 키(기본 Y)로 여는 빈 화면. 바닐라 채팅 입력창 없이 팝업만 조작한다.
 * 글자를 입력하거나 마우스를 쓰려면 화면이 하나 열려 있어야 해서 만든 것으로, 그리는 것은 없고
 * 팝업의 입력칸과 마우스 처리는 채팅창(T)을 열 때처럼 PopupChat 이 붙인다. Esc 나 (입력칸 밖에서) Y 로 닫는다.
 * <p>
 * 클래스 이름·패키지를 바꾸지 말 것: Together Plus 가 "com.chattingplus.PopupScreen" 이라는 이름으로 이 화면을 찾는다
 * (의존성이 없어서 바꿔도 빌드 오류가 나지 않고 Together Plus 의 Y 팝업만 조용히 깨진다).
 */
public class PopupScreen extends Screen {
	public PopupScreen() {
		super(Text.translatable("key.chattingplus.popup"));
	}

	/** 게임 화면을 어둡게 하거나 흐리지 않는다 */
	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	/** 입력칸을 선택하지 않은 상태(팝업 바깥을 누른 뒤)에서는 팝업 키를 한 번 더 누르면 닫힌다 */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!(getFocused() instanceof TextFieldWidget) && ChattingPlusClient.popupKey().matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
