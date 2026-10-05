// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.stagecue;

import jakarta.persistence.*;
import java.time.Instant;

/** cue_execution 的持久化事实；官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "cue_execution")
public class CueExecution {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "run_id", nullable = false)
  public Long runId;

  @Column(name = "source_cue_id", nullable = false)
  public Long sourceCueId;

  @Column(name = "cue_sequence", nullable = false)
  public int sequence;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "kind", nullable = false, length = 60)
  public String kind;

  @Column(name = "trigger_text", nullable = false, length = 300)
  public String triggerText;

  @Column(name = "description", nullable = false, length = 1000)
  public String description;

  @Column(name = "is_optional", nullable = false)
  public boolean optional;

  @Column(name = "operator_id", nullable = true)
  public Long operatorId;

  @Column(name = "received_at", nullable = true)
  public Instant receivedAt;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "standby_at", nullable = true)
  public Instant standbyAt;

  @Column(name = "ready_at", nullable = true)
  public Instant readyAt;

  @Column(name = "called_at", nullable = true)
  public Instant calledAt;

  @Column(name = "done_at", nullable = true)
  public Instant doneAt;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
