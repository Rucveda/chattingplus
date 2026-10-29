import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 모드 아이콘 만들기: 파란 둥근 사각형 위에 흰 말풍선과 점 세 개.
 * 실행: java tools/BuildIcon.java <프로젝트 폴더>
 * 결과: src/main/resources/assets/chattingplus/icon.png (128x128)
 */
public class BuildIcon {
	public static void main(String[] args) throws Exception {
		int s = 128;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		// 바탕: 위에서 아래로 밝은 파랑 → 진한 파랑
		g.setPaint(new GradientPaint(0, 0, new Color(0x6FA2F7), 0, s, new Color(0x3D6FD9)));
		g.fill(new RoundRectangle2D.Float(4, 4, s - 8, s - 8, 36, 36));

		// 말풍선 (아래 왼쪽에 꼬리)
		Path2D bubble = new Path2D.Float();
		bubble.append(new RoundRectangle2D.Float(22, 28, 84, 60, 30, 30), false);
		Path2D tail = new Path2D.Float();
		tail.moveTo(38, 80);
		tail.lineTo(30, 102);
		tail.lineTo(58, 84);
		tail.closePath();
		g.setColor(Color.WHITE);
		g.fill(bubble);
		g.fill(tail);

		// 점 세 개
		g.setColor(new Color(0x3D6FD9));
		for (int i = 0; i < 3; i++) g.fill(new Ellipse2D.Float(42 + i * 18, 52, 12, 12));

		g.dispose();
		File out = new File(args[0], "src/main/resources/assets/chattingplus/icon.png");
		ImageIO.write(img, "png", out);
		System.out.println("wrote " + out);
	}
}
