package xyz.attacktive.wallhavend;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import xyz.attacktive.wallhavend.domain.model.AppSettingsKt;

public class ClosestAspectRatioTest {
	@Test
	public void exactMatch_1080x1920_returns9x16() {
		assertEquals("9x16", AppSettingsKt.closestAspectRatio(1080, 1920));
	}

	@Test
	public void nonExactMatch_1080x2400_fallsBackTo9x16() {
		assertEquals("9x16", AppSettingsKt.closestAspectRatio(1080, 2400));
	}

	@Test
	public void widescreen_2560x1080_returns21x9() {
		assertEquals("21x9", AppSettingsKt.closestAspectRatio(2560, 1080));
	}

	@Test
	public void square_1024x1024_returns1x1() {
		assertEquals("1x1", AppSettingsKt.closestAspectRatio(1024, 1024));
	}

	@Test
	public void tallRatio_1080x2520_fallsBackTo9x16_notSparseRatio() {
		assertEquals("9x16", AppSettingsKt.closestAspectRatio(1080, 2520));
	}

}
