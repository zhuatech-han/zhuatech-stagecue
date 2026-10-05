// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.stagecue;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 数据库存储与首次响应统一微秒精度，避免重复请求出现纳秒差异。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class BusinessTime {
  private BusinessTime() {}

  /** 读取UTC业务时刻并匹配MySQL timestamp(6)精度。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Instant now(Clock clock) {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  /** 计划时刻符合MySQL时间范围并统一微秒精度。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Instant planned(Instant value) {
    if (value == null
        || value.isBefore(Instant.parse("1970-01-01T00:00:01Z"))
        || !value.isBefore(Instant.parse("2038-01-19T03:14:08Z")))
      throw new Problem(400, "INVALID_TIME");
    return value.truncatedTo(ChronoUnit.MICROS);
  }
}
