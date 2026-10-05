// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.stagecue;

import jakarta.persistence.*;

/** cue_definition 的持久化事实；官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "cue_definition")
public class CueDefinition {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "book_id", nullable = false)
  public Long bookId;

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

  @Column(name = "enabled", nullable = false)
  public boolean enabled;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
