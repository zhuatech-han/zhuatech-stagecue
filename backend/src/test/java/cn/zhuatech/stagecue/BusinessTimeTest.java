// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.stagecue;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

/** 时间事实在首次响应、保存和重试间保持一致精度。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class BusinessTimeTest {
  @Test
  void plannedNanosecondsCannotDivergeAfterPersistence() {
    assertEquals(
        Instant.parse("2026-10-06T12:00:00.123456Z"),
        BusinessTime.planned(Instant.parse("2026-10-06T12:00:00.123456789Z")));
  }

  @Test
  void unsupportedDatabaseTimesRejected() {
    assertThrows(Problem.class, () -> BusinessTime.planned(Instant.parse("2040-01-01T00:00:00Z")));
    assertThrows(Problem.class, () -> BusinessTime.planned(Instant.EPOCH));
    assertThrows(Problem.class, () -> BusinessTime.planned(null));
  }

  @Test
  void mysqlLastSupportedMicrosecondAccepted() {
    assertEquals(
        Instant.parse("2038-01-19T03:14:07.999999Z"),
        BusinessTime.planned(Instant.parse("2038-01-19T03:14:07.999999Z")));
  }

  @Test
  void microsecondsPreserved() {
    assertEquals(
        Instant.parse("2026-10-06T00:00:00.123456Z"),
        BusinessTime.now(
            Clock.fixed(Instant.parse("2026-10-06T00:00:00.123456789Z"), ZoneOffset.UTC)));
  }

  @Test
  void serverZoneDoesNotAlterInstant() {
    var at = Instant.parse("2026-10-06T00:00:00Z");
    assertEquals(at, BusinessTime.now(Clock.fixed(at, ZoneId.of("Asia/Shanghai"))));
  }

  @Test
  void truncationNeverAdvancesFacts() {
    var at = Instant.parse("2026-10-06T00:00:00.999999999Z");
    assertTrue(BusinessTime.now(Clock.fixed(at, ZoneOffset.UTC)).isBefore(at));
  }
}
