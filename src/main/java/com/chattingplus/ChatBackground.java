package com.chattingplus;

import com.chattingplus.ModConfig.Background;
import com.chattingplus.WhisperStore.Conversation;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 대화 영역 배경 그림. config/chattingplus/backgrounds 안의 그림을 골라 메인 창과 팝업의 대화 영역에 깐다.
 * 그림은 비율을 지키며 설정한 크기와 위치로 놓고(기본은 영역을 꽉 채우고 가운데 맞춤), 테마 바탕색 위에 설정한 진하기로 겹친다.
 * 설정 화면에서 정한 기본 배경을 쓰고, 채널 대화와 즐겨찾기한 귓속말은 대화마다 따로 정한 배경을 쓸 수 있다.
 */
public final class ChatBackground {
	/** 큰 사진은 긴 변을 이만큼으로 줄인다 (그래픽 메모리 절약, 대화 영역에는 이 정도면 충분) */
	private static final int MAX_SIZE = 1280;
	private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg"};

	private static final ImageCache images = new ImageCache("background", ChatBackground::dir, file -> {
		NativeImage image = ImageFiles.readStill(file, MAX_SIZE);
		return image == null ? null : new ImageFiles.Sheet(image, image.getWidth(), image.getHeight(), 1, new int[]{1});
	});

	private ChatBackground() {}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("chattingplus").resolve("backgrounds");
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
	public static List<String> files() {
		List<String> list = new ArrayList<>();
		if (!Files.isDirectory(dir())) return list;
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir())) {
			for (Path file : files) {
				String name = file.getFileName().toString();
				if (Files.isRegularFile(file) && supported(name)) list.add(name);
			}
		} catch (IOException e) {
			ChattingPlusClient.LOGGER.error("Failed to list {}", dir(), e);
		}
		list.sort(String.CASE_INSENSITIVE_ORDER);
		return list;
	}

	static boolean supported(String fileName) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		for (String ext : EXTENSIONS) if (lower.endsWith(ext)) return true;
		return false;
	}

	/** 같은 이름의 파일을 바꿔 넣었을 때 다시 읽게 한다 */
	public static void reload() {
		images.clear();
	}

	/** 그 대화가 따로 배경을 가질 수 있는지: 채널 대화와 즐겨찾기한 귓속말만 (귓속말 상대가 많아도 설정이 쌓이지 않게) */
	public static boolean canCustomize(Conversation c) {
		return c != null && (c.channel() != null || c.favorite);
	}

	/** 그 대화에 쓸 배경: 따로 정한 게 있으면 그것, 없으면 기본 배경 */
	public static Background of(Conversation c) {
		return canCustomize(c) && c.background != null ? c.background : ModConfig.get().background;
	}

	/** 메인 창(popup=false)이나 팝업에 이 배경 그림을 그리는지 */
	public static boolean enabled(Background bg, boolean popup) {
		if (bg.image == null || bg.image.isEmpty()) return false;
		return popup ? ModConfig.get().backgroundPopup : ModConfig.get().backgroundMain;
	}

	/**
	 * (x0,y0)-(x1,y1) 에 배경 그림을 그린다. 둥근 모서리는 tl/tr/bl/br 로 고른 모서리만 반지름 r 로 깎는다.
	 * alpha 는 설정한 진하기에 더 곱할 값 (팝업이 흐려질 때)
	 */
	public static void draw(DrawContext context, Background bg, int x0, int y0, int x1, int y1, int r,
							boolean tl, boolean tr, boolean bl, boolean br, float alpha) {
		if (x1 <= x0 || y1 <= y0) return;
		ImageCache.Entry entry = images.get(bg.image);
		if (entry == null || entry.texture == null) return;
		int a = Math.round(255 * alpha * bg.opacity / 100f);
		if (a <= 0) return;
		int color = Theme.withAlpha(0xFFFFFF, a);

		int texW = entry.texWidth, texH = entry.texHeight;
		int[] p = place(x1 - x0, y1 - y0, texW, texH, bg.zoom, bg.x, bg.y);
		int dx = x0 + p[0], dy = y0 + p[1], dw = p[2], dh = p[3];

		// 영역 밖으로 나간 부분은 잘라낸다. 모서리 모양은 창과 같은 계산으로 줄마다 잘라 보이는 부분에만 그린다
		Shapes.fill((a0, b0, c0, d0) -> {
			context.enableScissor(a0, b0, c0, d0);
			context.drawTexture(SmoothTextures::layer, entry.texture, dx, dy, 0, 0, dw, dh, texW, texH, texW, texH, color);
			context.disableScissor();
		}, x0, y0, x1, y1, r, tl, tr, bl, br);
	}

	/**
	 * 그림을 놓을 자리 {영역 안 x, y, 너비, 높이}. 크기 100% 는 영역을 꽉 채우는(비율 유지, 넘치는 쪽은 잘리는) 크기이고,
	 * 위치 0/50/100% 는 그림의 왼쪽(위)/가운데/오른쪽(아래)을 영역의 같은 쪽에 맞춘다.
	 * 그림이 영역보다 크면 어느 부분을 보여줄지, 작으면 어디에 놓을지가 된다.
	 */
	static int[] place(int areaW, int areaH, int texW, int texH, int zoomPercent, int xPercent, int yPercent) {
		double cover = Math.max((double) areaW / texW, (double) areaH / texH);
		double scale = cover * zoomPercent / 100.0;
		int w = Math.max(1, (int) Math.round(texW * scale)), h = Math.max(1, (int) Math.round(texH * scale));
		int x = (int) Math.round((areaW - w) * xPercent / 100.0), y = (int) Math.round((areaH - h) * yPercent / 100.0);
		return new int[]{x, y, w, h};
	}
}
