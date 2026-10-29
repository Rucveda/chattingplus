package com.chattingplus;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 둥근 창: 안쪽 칠(반지름 r)과 1픽셀 테두리를 합치면 바깥 모양(반지름 r+1)을 빈틈없이, 겹침 없이 덮어야 한다.
 * 예전에 특정 반지름에서 모서리에 빈 픽셀이 생겼던 문제를 막는다.
 */
class ShapesTest {
	private static final int W = 60, H = 40;

	private static int[][] paint(java.util.function.Consumer<Shapes.Painter> draw) {
		int[][] grid = new int[H][W];
		draw.accept((x0, y0, x1, y1) -> {
			for (int y = Math.max(0, y0); y < Math.min(H, y1); y++) {
				for (int x = Math.max(0, x0); x < Math.min(W, x1); x++) grid[y][x]++;
			}
		});
		return grid;
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
	void ringAndFillCoverOuterShapeExactly(int r) {
		int[][] expected = paint(p -> Shapes.fill(p, 0, 0, W, H, r == 0 ? 0 : r + 1, true, true, true, true));
		int[][] actual = paint(p -> {
			Shapes.fill(p, 1, 1, W - 1, H - 1, r, true, true, true, true);
			Shapes.ring(p, 0, 0, W, H, r, true, true, true, true);
		});
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				assertEquals(expected[y][x], actual[y][x], "r=" + r + " pixel (" + x + "," + y + ")");
			}
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {3, 6, 10})
	void onlyChosenCornersAreRounded(int r) {
		// 팝업처럼 아래 모서리만 둥글게: 위쪽 모서리 픽셀은 칠해져 있어야 한다
		int[][] grid = paint(p -> Shapes.fill(p, 0, 0, W, H, r, false, false, true, true));
		assertEquals(1, grid[0][0]);
		assertEquals(1, grid[0][W - 1]);
		assertEquals(0, grid[H - 1][0]);
		assertEquals(0, grid[H - 1][W - 1]);
	}
}
