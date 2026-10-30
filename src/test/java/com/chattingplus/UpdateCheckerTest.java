package com.chattingplus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
	@Test
	void readsGithubLatestRelease() {
		String body = """
				{"tag_name":"v1.13.0","html_url":"https://github.com/rucveda/chattingplus/releases/tag/v1.13.0",
				 "body":"\\r\\n## 이모티콘 8종 추가\\r\\n- 팝업 버그 수정","draft":false,"prerelease":false}""";
		UpdateChecker.Release release = UpdateChecker.parse(body);
		assertNotNull(release);
		assertEquals("1.13.0", release.version());
		assertEquals("이모티콘 8종 추가", release.message());
	}

	@Test
	void missingBodyIsNull() {
		UpdateChecker.Release release = UpdateChecker.parse("{\"tag_name\":\"1.13.0\",\"html_url\":\"https://evil.example/x\",\"body\":null}");
		assertNotNull(release);
		assertNull(release.message());
	}

	@Test
	void notAReleaseIsIgnored() {
		assertNull(UpdateChecker.parse("{\"message\":\"Not Found\"}"));
		assertNull(UpdateChecker.parse("not json"));
		assertNull(UpdateChecker.parse("[]"));
	}

	@Test
	void versionsCompareNumerically() {
		assertTrue(UpdateChecker.compare("1.13.0", "1.12.0") > 0);
		assertTrue(UpdateChecker.compare("1.10.0", "1.9.2") > 0);
		assertEquals(0, UpdateChecker.compare("1.12", "1.12.0"));
		assertTrue(UpdateChecker.compare("1.12.0", "1.12.1") < 0);
	}
}
