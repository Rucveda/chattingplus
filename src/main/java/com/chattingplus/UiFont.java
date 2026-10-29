package com.chattingplus;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 모드 창/팝업 글꼴과 글자 그리기.
 *
 * 사용자 글꼴: config/chattingplus/fonts 에 .ttf/.otf 를 넣고 "글꼴 새로고침"을 누르면,
 * resourcepacks/chattingplus-fonts 리소스팩으로 복사하고 글꼴 정의를 만들어 켠 다음 리소스를 다시 읽는다.
 * (마인크래프트는 리소스팩의 TTF 글꼴만 읽을 수 있어서 이렇게 한다)
 */
public final class UiFont {
	public static final String DEFAULT = "default";
	public static final String UNIFORM = "uniform";

	private static final String PACK_DIR = "chattingplus-fonts";
	private static final String PACK_ID = "file/" + PACK_DIR;
	/** 1.21.4 리소스팩 형식 */
	private static final int PACK_FORMAT = 46;

	public record Choice(String id, String name) {}

	private UiFont() {}

	// ---------------------------------------------------------------- 그리기

	private static Style style() {
		String font = ModConfig.get().font;
		if (font == null || DEFAULT.equals(font)) return Style.EMPTY;
		if (UNIFORM.equals(font)) return Style.EMPTY.withFont(Identifier.ofVanilla("uniform"));
		Identifier id = Identifier.tryParse(font);
		return id == null ? Style.EMPTY : Style.EMPTY.withFont(id);
	}

	public static MutableText text(String s) {
		return Text.literal(s).setStyle(style());
	}

	public static int width(TextRenderer tr, String s) {
		return tr.getWidth(text(s));
	}

	/** 폭에 맞게 뒤를 자른다 */
	public static String trim(TextRenderer tr, String s, int width) {
		if (width <= 0) return "";
		return tr.getTextHandler().trimToWidth(s, width, style());
	}

	/** 테마의 글자 그림자 설정을 따른다 */
	public static void draw(DrawContext context, TextRenderer tr, String s, int x, int y, int color) {
		context.drawText(tr, text(s), x, y, color, Theme.get().textShadow);
	}

	public static void drawCentered(DrawContext context, TextRenderer tr, String s, int cx, int y, int color) {
		draw(context, tr, s, cx - width(tr, s) / 2, y, color);
	}

	// ---------------------------------------------------------------- 글자 크기 단계

	public static final float NORMAL = 1f;

	/**
	 * 시간, 미리보기, 안내 같은 보조 글자 배율.
	 * 한글은 16픽셀 그림 글꼴이라 화면 픽셀에 딱 맞지 않게 줄이면 획이 뭉개진다. 그래서 GUI 배율에 맞춰
	 * 글자 한 칸이 정수 픽셀이 되도록 고른다 (배율 4 → 3/4, 3 → 2/3). 배율 2 이하는 줄일 여유가 없어 그대로.
	 */
	public static float small() {
		int gui = guiScale();
		return gui >= 3 ? (gui - 1) / (float) gui : 1f;
	}

	/** 창 제목, 대화 상대 이름 배율 (배율 4 → 5/4, 3 → 4/3, 2 → 3/2) */
	public static float title() {
		int gui = guiScale();
		return gui >= 2 ? (gui + 1) / (float) gui : 1f;
	}

	private static int guiScale() {
		return Math.max(1, (int) Math.round(MinecraftClient.getInstance().getWindow().getScaleFactor()));
	}

	public static int width(TextRenderer tr, String s, float scale) {
		return Math.round(width(tr, s) * scale);
	}

	/** 배율을 곱한 글자 높이 */
	public static int height(float scale) {
		return Math.round(8 * scale);
	}

	public static String trim(TextRenderer tr, String s, int width, float scale) {
		return trim(tr, s, (int) (width / scale));
	}

	/** 테마의 그림자 설정을 따른다 */
	public static void draw(DrawContext context, TextRenderer tr, String s, int x, int y, int color, float scale) {
		drawText(context, tr, text(s), x, y, color, scale, Theme.get().textShadow);
	}

	/** 그림자 없이 */
	public static void drawPlain(DrawContext context, TextRenderer tr, String s, int x, int y, int color, float scale) {
		drawText(context, tr, text(s), x, y, color, scale, false);
	}

	/**
	 * 서버가 보낸 모양의 이름표(등급 아이콘, 칭호, 색)를 그대로 그린다. 모드 글꼴은 적용하지 않는다 (아이콘이 깨지므로).
	 * 폭이 넘치면 자른다. 그린 폭을 돌려준다.
	 */
	public static int drawStyled(DrawContext context, TextRenderer tr, Text label, int x, int y, int maxWidth, int color, float scale) {
		OrderedText line = Language.getInstance().reorder(tr.trimToWidth(label, (int) (maxWidth / scale)));
		drawText(context, tr, line, x, y, color, scale, false);
		return Math.round(tr.getWidth(line) * scale);
	}

	/** 줄바꿈해 둔 한 줄(OrderedText)을 배율에 맞춰 그린다. 모든 배율 그리기는 여기로 모인다. */
	public static void drawText(DrawContext context, TextRenderer tr, OrderedText text, int x, int y, int color, float scale, boolean shadow) {
		if (scale == 1f) {
			context.drawText(tr, text, x, y, color, shadow);
			return;
		}
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0);
		context.getMatrices().scale(scale, scale, 1);
		context.drawText(tr, text, 0, 0, color, shadow);
		context.getMatrices().pop();
	}

	public static void drawText(DrawContext context, TextRenderer tr, Text text, int x, int y, int color, float scale, boolean shadow) {
		drawText(context, tr, text.asOrderedText(), x, y, color, scale, shadow);
	}

	// ---------------------------------------------------------------- 사용자 글꼴

	public static Path fontsDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("chattingplus").resolve("fonts");
	}

	private static Path packDir() {
		return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(PACK_DIR);
	}

	private static Path packFontDir() {
		return packDir().resolve("assets").resolve(ChattingPlusClient.MOD_ID).resolve("font");
	}

	public static void openFontsFolder() {
		try {
			Files.createDirectories(fontsDir());
		} catch (IOException e) {
			ChattingPlusClient.LOGGER.error("Failed to create {}", fontsDir(), e);
		}
		Util.getOperatingSystem().open(fontsDir().toUri());
	}

	/** 고를 수 있는 글꼴: 기본, 유니코드, 리소스팩에 등록된 사용자 글꼴 */
	public static List<Choice> choices() {
		List<Choice> list = new ArrayList<>();
		list.add(new Choice(DEFAULT, "기본"));
		list.add(new Choice(UNIFORM, "유니코드"));
		for (Map.Entry<String, String> e : registeredFonts().entrySet()) {
			list.add(new Choice(ChattingPlusClient.MOD_ID + ":" + e.getKey(), e.getValue()));
		}
		return list;
	}

	public static String nameOf(String id) {
		for (Choice c : choices()) if (c.id.equals(id)) return c.name;
		return "기본";
	}

	/** 글꼴 정의 이름 → 표시 이름 (리소스팩 폴더의 names.txt) */
	private static Map<String, String> registeredFonts() {
		Map<String, String> map = new LinkedHashMap<>();
		Path names = packFontDir().resolve("names.txt");
		if (!Files.exists(names)) return map;
		try {
			for (String line : Files.readAllLines(names, StandardCharsets.UTF_8)) {
				int tab = line.indexOf('\t');
				if (tab > 0) map.put(line.substring(0, tab), line.substring(tab + 1));
			}
		} catch (IOException e) {
			ChattingPlusClient.LOGGER.error("Failed to read {}", names, e);
		}
		return map;
	}

	/**
	 * 글꼴 폴더의 파일을 리소스팩으로 옮기고, 리소스팩을 켜고, 리소스를 다시 읽는다.
	 * @return 등록한 글꼴 수
	 */
	public static int syncAndReload() {
		int count = 0;
		try {
			Files.createDirectories(fontsDir());
			Path fontDir = packFontDir();
			// 예전에 등록한 글꼴은 지우고 새로 만든다
			if (Files.exists(fontDir)) {
				try (Stream<Path> old = Files.walk(fontDir)) {
					old.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
						try {
							Files.delete(p);
						} catch (IOException ignored) {
						}
					});
				}
			}
			Files.createDirectories(fontDir.resolve("user"));
			Files.writeString(packDir().resolve("pack.mcmeta"),
					"{\"pack\":{\"pack_format\":" + PACK_FORMAT + ",\"description\":\"Chatting Plus 글꼴 (자동 생성)\"}}\n", StandardCharsets.UTF_8);

			StringBuilder names = new StringBuilder();
			try (DirectoryStream<Path> files = Files.newDirectoryStream(fontsDir())) {
				for (Path file : files) {
					String fileName = file.getFileName().toString();
					String lower = fileName.toLowerCase(Locale.ROOT);
					if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) continue;
					String display = fileName.substring(0, fileName.length() - 4);
					String key = "user_" + safeId(display, count);
					String ext = lower.substring(lower.length() - 3);
					Files.copy(file, fontDir.resolve("user").resolve(key + "." + ext), StandardCopyOption.REPLACE_EXISTING);
					Files.writeString(fontDir.resolve(key + ".json"),
							"{\"providers\":[{\"type\":\"ttf\",\"file\":\"" + ChattingPlusClient.MOD_ID + ":user/" + key + "." + ext + "\","
									+ "\"size\":10.0,\"oversample\":4.0,\"shift\":[0.0,1.0]}]}\n", StandardCharsets.UTF_8);
					names.append(key).append('\t').append(display).append('\n');
					count++;
				}
			}
			Files.writeString(fontDir.resolve("names.txt"), names.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ChattingPlusClient.LOGGER.error("Failed to register fonts", e);
		}

		MinecraftClient client = MinecraftClient.getInstance();
		ResourcePackManager manager = client.getResourcePackManager();
		manager.scanPacks();
		if (!manager.getEnabledIds().contains(PACK_ID) && manager.getIds().contains(PACK_ID)) {
			List<String> enabled = new ArrayList<>(manager.getEnabledIds());
			enabled.add(PACK_ID);
			manager.setEnabledProfiles(enabled);
		}
		client.options.refreshResourcePacks(manager);
		return count;
	}

	/** 리소스 이름으로 쓸 수 있게 영문 소문자/숫자/_ 만 남긴다. 한글 이름이면 순번으로 */
	private static String safeId(String name, int index) {
		String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_");
		if (id.replace("_", "").isEmpty()) id = "font" + index;
		return id + "_" + index;
	}
}
