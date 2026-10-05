// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.stagecue;

import jakarta.persistence.*;
import java.time.Instant;

/** show_run 的持久化事实；官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "show_run")
public class ShowRun {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", nullable = false, length = 60)
  public String reference;

  @Column(name = "book_id", nullable = false)
  public Long bookId;

  @Column(name = "production_id", nullable = false)
  public Long productionId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "caller_id", nullable = false)
  public Long callerId;

  @Column(name = "supervisor_id", nullable = false)
  public Long supervisorId;

  @Column(name = "production_name", nullable = false, length = 120)
  public String productionName;

  @Column(name = "book_title", nullable = false, length = 120)
  public String bookTitle;

  @Column(name = "book_revision", nullable = false)
  public int bookRevision;

  @Column(name = "book_hash", nullable = false, length = 64)
  public String bookHash;

  @Column(name = "location", nullable = false, length = 200)
  public String location;

  @Column(name = "planned_at", nullable = false)
  public Instant plannedAt;

  @Column(name = "mode", nullable = false, length = 30)
  public String mode;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "outcome", nullable = true, length = 30)
  public String outcome;

  @Column(name = "started_at", nullable = true)
  public Instant startedAt;

  @Column(name = "ended_at", nullable = true)
  public Instant endedAt;

  @Column(name = "closed_at", nullable = true)
  public Instant closedAt;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
