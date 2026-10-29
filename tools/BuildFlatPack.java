import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.geometry.size.FloatSize;
import com.github.weisj.jsvg.parser.SVGLoader;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * 기본 이모티콘 팩과 버튼 아이콘 만들기.
 * - 이모티콘: Twemoji (그림 CC-BY 4.0, https://github.com/jdecked/twemoji) SVG 를 64/128/256px PNG 로
 * - 아이콘: Google Material Symbols Rounded (Apache 2.0) SVG 를 흰색 64px PNG 로 (게임에서 테마 색으로 칠한다)
 *
 * 준비: <원본 폴더>/svg/{코드포인트}.svg, <원본 폴더>/icons/{add,settings,mood,close}.svg,
 *       <원본 폴더>/LICENSE-twemoji-graphics, <원본 폴더>/LICENSE-material-icons
 * 실행: java -cp jsvg-1.6.1.jar tools/BuildFlatPack.java <원본 폴더> <프로젝트 폴더>
 */
public class BuildFlatPack {
	/** {팩 안 id, 채팅에 오가는 글자, 이름, Twemoji 코드포인트} */
	static final String[][] EMOTES = {
		{"smile", ":웃음:", "웃음", "1f604"},
		{"laugh", ":크크:", "크크", "1f602"},
		{"love", ":하트:", "하트", "1f60d"},
		{"wink", ":윙크:", "윙크", "1f61c"},
		{"sad", ":슬픔:", "슬픔", "1f614"},
		{"cry", ":엉엉:", "엉엉", "1f62d"},
		{"tear", ":ㅠㅠ:", "ㅠㅠ", "1f622"},
		{"angry", ":화남:", "화남", "1f621"},
		{"wow", ":놀람:", "놀람", "1f632"},
		{"scream", ":헉:", "헉", "1f631"},
		{"blush", ":당황:", "당황", "1f633"},
		{"sweat", ":머쓱:", "머쓱", "1f605"},
		{"think", ":갸웃:", "갸웃", "1f914"},
		{"smirk", ":씨익:", "씨익", "1f60f"},
		{"cool", ":쿨:", "쿨", "1f60e"},
		{"please", ":부탁:", "부탁", "1f97a"},
		{"shh", ":쉿:", "쉿", "1f92b"},
		{"sleepy", ":졸림:", "졸림", "1f634"},
		{"yawn", ":하품:", "하품", "1f971"},
		{"wave", ":안녕:", "안녕", "1f44b"},
		{"thanks", ":감사:", "감사", "1f64f"},
		{"thumbsup", ":최고:", "최고", "1f44d"},
		{"ok", ":오케이:", "오케이", "1f44c"},
		{"clap", ":박수:", "박수", "1f44f"},
		{"fighting", ":화이팅:", "화이팅", "1f4aa"},
		{"party", ":축하:", "축하", "1f389"},
		{"heart", ":사랑:", "사랑", "2764"},
		{"fire", ":불꽃:", "불꽃", "1f525"},
	};
	/** {아이콘 이름, Material Symbols 원본 이름} */
	static final String[][] ICONS = {{"add", "add"}, {"settings", "settings"}, {"emote", "mood"}, {"close", "close"}};
	/** 화면에 그려지는 크기에 맞는 그림을 고를 수 있도록 여러 크기로 만든다 */
	static final int[] EMOTE_SIZES = {64, 128, 256};
	static final int ICON_SIZE = 64;

	public static void main(String[] args) throws Exception {
		Path src = Path.of(args[0]);
		Path res = Path.of(args[1], "src/main/resources/assets/chattingplus");
		Path textures = res.resolve("textures/emote/basic");
		Path icons = res.resolve("textures/gui/icon");
		Path emotes = res.resolve("emotes");

		clear(textures);
		for (String[] e : EMOTES) {
			for (int size : EMOTE_SIZES) {
				ImageIO.write(render(src.resolve("svg").resolve(e[3] + ".svg"), size), "png", textures.resolve(e[0] + "_" + size + ".png").toFile());
			}
		}

		clear(icons);
		for (String[] icon : ICONS) {
			BufferedImage img = render(src.resolve("icons").resolve(icon[1] + ".svg"), ICON_SIZE);
			// 흰색으로 바꾸고 투명도만 남긴다 (게임에서 색을 입힌다)
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) img.setRGB(x, y, (img.getRGB(x, y) & 0xFF000000) | 0xFFFFFF);
			}
			ImageIO.write(img, "png", icons.resolve(icon[0] + ".png").toFile());
		}
		Files.copy(src.resolve("LICENSE-material-icons"), icons.resolve("LICENSE-material-symbols.txt"), StandardCopyOption.REPLACE_EXISTING);

		StringBuilder sb = new StringBuilder();
		sb.append("{\n\t\"packs\": [\n\t\t{\n\t\t\t\"id\": \"basic\",\n\t\t\t\"name\": \"기본\",\n");
		sb.append("\t\t\t\"author\": \"Twemoji (CC-BY 4.0)\",\n\t\t\t\"sizes\": ").append(java.util.Arrays.toString(EMOTE_SIZES)).append(",\n\t\t\t\"emotes\": [\n");
		for (int i = 0; i < EMOTES.length; i++) {
			String[] e = EMOTES[i];
			sb.append("\t\t\t\t{\"id\": \"").append(e[0]).append("\", \"token\": \"").append(e[1]).append("\", \"name\": \"").append(e[2]).append("\"}");
			sb.append(i < EMOTES.length - 1 ? ",\n" : "\n");
		}
		sb.append("\t\t\t]\n\t\t}\n\t]\n}\n");
		Files.createDirectories(emotes);
		try (Stream<Path> old = Files.list(emotes)) {
			for (Path p : old.toList()) if (p.getFileName().toString().startsWith("LICENSE")) Files.delete(p);
		}
		Files.writeString(emotes.resolve("packs.json"), sb.toString(), StandardCharsets.UTF_8);
		Files.copy(src.resolve("LICENSE-twemoji-graphics"), emotes.resolve("LICENSE-twemoji.txt"), StandardCopyOption.REPLACE_EXISTING);
		Files.writeString(emotes.resolve("ATTRIBUTION.txt"),
				"기본 이모티콘 팩 그림: Twemoji (Copyright 2019 Twitter, Inc and other contributors; 2024 jdecked and contributors)\n"
						+ "https://github.com/jdecked/twemoji\n"
						+ "Licensed under CC-BY 4.0: https://creativecommons.org/licenses/by/4.0/\n"
						+ "SVG 원본을 128px PNG 로 변환해서 사용했습니다.\n", StandardCharsets.UTF_8);
		System.out.println("built " + EMOTES.length + " emotes, " + ICONS.length + " icons");
	}

	static BufferedImage render(Path svg, int size) throws Exception {
		SVGDocument doc = new SVGLoader().load(svg.toUri().toURL());
		if (doc == null) throw new IllegalStateException("cannot load " + svg);
		FloatSize docSize = doc.size();
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.scale(size / docSize.width, size / docSize.height);
		doc.render(null, g);
		g.dispose();
		bleed(img);
		return img;
	}

	/**
	 * 완전히 투명한 픽셀의 색을 옆 픽셀 색으로 채운다 (투명도는 그대로).
	 * 게임에서 부드럽게 늘리거나 줄여 그리면 테두리에서 투명 픽셀 색(검정)이 섞여 어두운 테두리가 생기는데, 그걸 막는다.
	 */
	static void bleed(BufferedImage img) {
		int w = img.getWidth(), h = img.getHeight();
		boolean[] known = new boolean[w * h];
		for (int i = 0; i < w * h; i++) known[i] = (img.getRGB(i % w, i / w) >>> 24) != 0;
		for (int pass = 0; pass < 6; pass++) {
			boolean[] next = known.clone();
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (known[y * w + x]) continue;
					int r = 0, gr = 0, b = 0, n = 0;
					for (int dy = -1; dy <= 1; dy++) {
						for (int dx = -1; dx <= 1; dx++) {
							int nx = x + dx, ny = y + dy;
							if (nx < 0 || ny < 0 || nx >= w || ny >= h || !known[ny * w + nx]) continue;
							int c = img.getRGB(nx, ny);
							r += (c >> 16) & 0xFF;
							gr += (c >> 8) & 0xFF;
							b += c & 0xFF;
							n++;
						}
					}
					if (n == 0) continue;
					img.setRGB(x, y, ((r / n) << 16) | ((gr / n) << 8) | (b / n)); // 투명도 0 유지
					next[y * w + x] = true;
				}
			}
			known = next;
		}
	}

	static void clear(Path dir) throws Exception {
		if (Files.exists(dir)) {
			try (Stream<Path> old = Files.list(dir)) {
				for (Path p : old.toList()) Files.delete(p);
			}
		}
		Files.createDirectories(dir);
	}
}
