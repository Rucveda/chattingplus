package com.chattingplus;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu 의 모드 목록에서 설정 버튼을 누르면 설정 화면을 연다 (Mod Menu 가 없으면 쓰이지 않는다) */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}
