// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.stagecue;

import jakarta.persistence.*;

/** book_editor 的持久化事实；官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "book_editor")
public class BookEditor {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "book_id", nullable = false)
  public Long bookId;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;
}
