package com.chattingplus;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 서버가 보낸 모양 그대로의 이름표(등급 아이콘, 칭호, 색이 있는 닉네임)를 기록 파일에 저장할 수 있게
 * 글자(JSON)로 바꾸고 되돌린다. 되돌린 결과는 같은 JSON 이면 다시 쓰도록 기억해 둔다.
 */
final class NameLabels {
	private static final int CACHE_SIZE = 512;
	private static final Map<String, Text> cache = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Text> eldest) {
			return size() > CACHE_SIZE;
		}
	};

	private NameLabels() {}

	private static RegistryWrapper.WrapperLookup registries() {
		ClientPlayNetworkHandler network = MinecraftClient.getInstance().getNetworkHandler();
		return network != null ? network.getRegistryManager() : DynamicRegistryManager.EMPTY;
	}

	static String toJson(Text label) {
		if (label == null) return null;
		try {
			return Text.Serialization.toJsonString(label, registries());
		} catch (Exception e) {
			return null;
		}
	}

	/** 저장된 이름표. 없거나 읽을 수 없으면 null */
	static Text fromJson(String json) {
		if (json == null || json.isEmpty()) return null;
		Text cached = cache.get(json);
		if (cached != null) return cached;
		Text text;
		try {
			text = Text.Serialization.fromJson(json, registries());
		} catch (Exception e) {
			text = null;
		}
		if (text != null) cache.put(json, text);
		return text;
	}
}
