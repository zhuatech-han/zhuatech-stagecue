// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.stagecue;

import jakarta.persistence.*;
import java.time.Instant;

/** run_hold 的持久化事实；官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "run_hold")
public class RunHold {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "run_id", nullable = false)
  public Long runId;

  @Column(name = "raised_by", nullable = false)
  public Long raisedBy;

  @Column(name = "resolved_by", nullable = true)
  public Long resolvedBy;

  @Column(name = "reason", nullable = false, length = 1000)
  public String reason;

  @Column(name = "resolution", nullable = true, length = 1000)
  public String resolution;

  @Column(name = "raised_at", nullable = false)
  public Instant raisedAt;

  @Column(name = "resolved_at", nullable = true)
  public Instant resolvedAt;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
