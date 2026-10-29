package com.chattingplus;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 모드에 들어 있는 이모티콘 팩들. assets/chattingplus/emotes/packs.json 에 팩과 이모티콘이 적혀 있고,
 * 그림은 assets/chattingplus/textures/emote/{팩 id}/ 아래 정사각형 PNG:
 * - 팩에 "sizes": [64, 128, 256] 처럼 여러 크기를 적으면 {이모티콘 id}_{크기}.png 들을 두고,
 *   화면에 그려지는 실제 픽셀 크기에 맞는 것을 골라 그린다 (크게 늘리거나 많이 줄이면 테두리가 거칠어져서)
 * - "size": 128 처럼 하나만 적으면 {이모티콘 id}.png 하나
 * 기본 팩은 Twemoji (CC-BY 4.0) 이고 tools/BuildFlatPack.java 로 만든다.
 * 다른 사람이 만든 팩은 packs.json 에 팩을 하나 더 적고 그림 폴더를 추가하면 고르는 창에 탭으로 생긴다.
 *
 * 채팅으로는 ":웃음:" 같은 글자로 오가고, 같은 모드를 쓰는 사람 화면에서만 그림으로 보인다.
 */
public final class Emotes {
	/** sizes: 준비된 그림 크기(작은 순). multi 가 false 면 크기 하나짜리 {id}.png */
	public record Emote(String packId, String id, String token, String name, int[] sizes, boolean multi) {
		/** 화면에서 screenPixels 픽셀로 보일 때 쓸 그림 크기: 그보다 크거나 같은 것 중 가장 작은 것 */
		public int pickSize(int screenPixels) {
			for (int size : sizes) if (size >= screenPixels) return size;
			return sizes[sizes.length - 1];
		}

		public Identifier texture(int size) {
			String file = multi ? id + "_" + size + ".png" : id + ".png";
			return Identifier.of(ChattingPlusClient.MOD_ID, "textures/emote/" + packId + "/" + file);
		}
	}

	public record Pack(String id, String name, String author, List<Emote> emotes) {}

	private static final Identifier MANIFEST = Identifier.of(ChattingPlusClient.MOD_ID, "emotes/packs.json");
	/** 한 메시지에 이모티콘만 있을 때 크게 보여주는 최대 개수 */
	private static final int MAX_STICKERS = 5;

	private static List<Pack> packs;
	private static final Map<String, Emote> byToken = new HashMap<>();

	private Emotes() {}

	public static List<Pack> packs() {
		if (packs == null) load();
		return packs;
	}

	public static Emote byToken(String token) {
		if (packs == null) load();
		return byToken.get(token);
	}

	/** 메시지가 이모티콘 글자로만 되어 있으면(공백 구분, 최대 5개) 그 이모티콘들, 아니면 null */
	public static List<Emote> stickers(String text) {
		String trimmed = text.trim();
		if (trimmed.isEmpty() || trimmed.charAt(0) != ':') return null;
		String[] parts = trimmed.split("\\s+");
		if (parts.length > MAX_STICKERS) return null;
		List<Emote> list = new ArrayList<>();
		for (String part : parts) {
			Emote emote = byToken(part);
			if (emote == null) return null;
			list.add(emote);
		}
		return list;
	}

	/** 리소스를 다시 읽으면(F3+T) 팩 목록도 다시 읽는다 */
	static void invalidate() {
		packs = null;
	}

	private static void load() {
		List<Pack> list = new ArrayList<>();
		byToken.clear();
		Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(MANIFEST);
		if (resource.isPresent()) {
			try (Reader reader = resource.get().getReader()) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				for (JsonElement packElement : root.getAsJsonArray("packs")) {
					JsonObject p = packElement.getAsJsonObject();
					String packId = p.get("id").getAsString();
					boolean multi = p.has("sizes");
					int[] sizes;
					if (multi) {
						JsonArray s = p.getAsJsonArray("sizes");
						sizes = new int[s.size()];
						for (int i = 0; i < sizes.length; i++) sizes[i] = s.get(i).getAsInt();
						java.util.Arrays.sort(sizes);
					} else {
						sizes = new int[]{p.has("size") ? p.get("size").getAsInt() : 64};
					}
					List<Emote> emotes = new ArrayList<>();
					JsonArray array = p.getAsJsonArray("emotes");
					for (JsonElement e : array) {
						JsonObject o = e.getAsJsonObject();
						String id = o.get("id").getAsString();
						String token = o.get("token").getAsString();
						String name = o.has("name") ? o.get("name").getAsString() : id;
						Emote emote = new Emote(packId, id, token, name, sizes, multi);
						if (byToken.putIfAbsent(token, emote) != null) {
							ChattingPlusClient.LOGGER.warn("[ChattingPlus] duplicate emote token {} in pack {}", token, packId);
							continue;
						}
						emotes.add(emote);
					}
					String author = p.has("author") ? p.get("author").getAsString() : "";
					list.add(new Pack(packId, p.get("name").getAsString(), author, List.copyOf(emotes)));
				}
			} catch (Exception e) {
				ChattingPlusClient.LOGGER.error("[ChattingPlus] failed to read emote packs", e);
			}
		}
		packs = List.copyOf(list);
	}
}
