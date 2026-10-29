package com.chattingplus;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 스티커 그림. 모드 이모티콘("emote:{글자}")이나 config/chattingplus/stickers 안의 그림 파일("file:{이름}").
 * 파일은 처음 그릴 때 따로 읽어서(게임이 멈추지 않게) 다 읽으면 텍스처로 올린다. 움직이는 GIF 는 프레임을 시간에 맞춰 바꿔 그린다.
 */
public final class Stickers {
	public static final String EMOTE = "emote:";
	public static final String FILE = "file:";
	/** 스티커는 크게 붙여도 200 GUI 픽셀이라 이 정도면 충분하다 */
	private static final int MAX_SIDE = 384;
	private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif"};

	private static final ImageCache files = new ImageCache("sticker", Stickers::dir, file -> ImageFiles.readSheet(file, MAX_SIDE));

	private Stickers() {}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("chattingplus").resolve("stickers");
	}

	public static void openFolder() {
		try {
			Files.createDirectories(dir());
		} catch (IOException e) {
			ChattingPlusClient.LOGGER.error("Failed to create {}", dir(), e);
		}
		Util.getOperatingSystem().open(dir().toUri());
	}

	/** 폴더 안의 그림 파일 이름 (가나다순) */
	public static List<String> fileNames() {
		List<String> list = new ArrayList<>();
		if (!Files.isDirectory(dir())) return list;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir())) {
			for (Path file : stream) {
				String name = file.getFileName().toString();
				String lower = name.toLowerCase(Locale.ROOT);
				for (String ext : EXTENSIONS) {
					if (lower.endsWith(ext) && Files.isRegularFile(file)) {
						list.add(name);
						break;
					}
				}
			}
		} catch (IOException e) {
			ChattingPlusClient.LOGGER.error("Failed to list {}", dir(), e);
		}
		list.sort(String.CASE_INSENSITIVE_ORDER);
		return list;
	}

	/** 그림 파일을 바꿔 넣었을 때: 올린 텍스처를 모두 버리고 다시 읽게 한다 */
	public static void reload() {
		files.clear();
	}

	/** 가로 ÷ 세로. 아직 읽는 중이거나 이모티콘이면 1 */
	public static float aspect(String source) {
		ImageCache.Entry entry = source.startsWith(FILE) ? files.get(source.substring(FILE.length())) : null;
		if (entry == null || entry.sheet == null) return 1f;
		return (float) entry.sheet.frameW() / entry.sheet.frameH();
	}

	/** 스티커를 (x,y) 에 w×h 로 그린다. 그림이 없거나 아직 읽는 중이면 false */
	public static boolean draw(DrawContext context, String source, int x, int y, int w, int h, int color) {
		if (source.startsWith(EMOTE)) {
			Emotes.Emote emote = Emotes.byToken(source.substring(EMOTE.length()));
			if (emote == null) return false;
			SmoothTextures.drawEmote(context, emote, x, y, Math.min(w, h), color);
			return true;
		}
		if (!source.startsWith(FILE)) return false;
		ImageCache.Entry entry = files.get(source.substring(FILE.length()));
		if (entry == null || entry.texture == null) return false;
		ImageFiles.Sheet sheet = entry.sheet;
		int frame = sheet.frameAt(Util.getMeasuringTimeMs());
		context.drawTexture(SmoothTextures::layer, entry.texture, x, y, sheet.u(frame), sheet.v(frame), w, h,
				sheet.frameW(), sheet.frameH(), entry.texWidth, entry.texHeight, color);
		return true;
	}
}
