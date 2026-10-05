// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.stagecue;

import jakarta.persistence.*;
import java.time.Instant;

/** cue_book 的持久化事实；官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "cue_book")
public class CueBook {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "production_id", nullable = false)
  public Long productionId;

  @Column(name = "revision", nullable = false)
  public int revision;

  @Column(name = "title", nullable = false, length = 120)
  public String title;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "approved_by", nullable = true)
  public Long approvedBy;

  @Column(name = "content_hash", nullable = true, length = 64)
  public String contentHash;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "approved_at", nullable = true)
  public Instant approvedAt;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
