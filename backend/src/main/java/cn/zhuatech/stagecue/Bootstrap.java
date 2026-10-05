// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.stagecue;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库初始化岗位与提示类型，业务数据保持为空。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${stagecue.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 注册真实接口权限和管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var d = new Department();
    d.name = "总部";
    db.save(d);
    String[][] ps = {
      {"production.read", "查看制作目录"},
      {"production.write", "维护制作目录"},
      {"book.read", "查看提示本"},
      {"book.write", "编辑和提交提示本"},
      {"book.review", "独立审批提示本"},
      {"run.read", "查看授权场次"},
      {"run.write", "编排场次与岗位"},
      {"run.call", "指定提示员推进场次"},
      {"cue.operate", "指定操作员确认与回报"},
      {"run.hold", "指定成员提出暂停"},
      {"run.review", "指定独立监督与复盘"},
      {"dashboard", "场次统计"},
      {"export", "授权证据导出"},
      {"audit", "操作审计"},
      {"admin", "系统管理"}
    };
    var all = new HashSet<String>();
    for (var row : ps) {
      var p = new Permission();
      p.code = row[0];
      p.name = row[1];
      db.save(p);
      all.add(p.code);
    }
    var admin = role("管理员", "ALL", all);
    role(
        "制作协调",
        "DEPARTMENT",
        Set.of(
            "production.read",
            "production.write",
            "book.read",
            "book.write",
            "run.read",
            "run.write",
            "dashboard",
            "export",
            "audit"));
    role(
        "独立监督",
        "DEPARTMENT",
        Set.of(
            "production.read",
            "book.read",
            "book.review",
            "run.read",
            "run.review",
            "dashboard",
            "export",
            "audit"));
    role("提示员", "SELF", Set.of("run.read", "run.call", "run.hold", "dashboard", "export"));
    role("技术操作员", "SELF", Set.of("run.read", "cue.operate", "run.hold", "dashboard", "export"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.roleId = admin.id;
    a.departmentId = d.id;
    a.enabled = true;
    a.passwordHash = encoder.encode(password);
    db.save(a);
    String[][] ms = {
      {"runs", "场次执行", "Show runs", "run.read"},
      {"books", "提示本版次", "Cue books", "book.read"},
      {"productions", "制作目录", "Productions", "production.read"},
      {"dashboard", "场次统计", "Statistics", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门管理", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "提示类型", "Cue types", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < ms.length; i++) {
      var m = new NavMenu();
      m.code = ms[i][0];
      m.name = ms[i][1];
      m.nameEn = ms[i][2];
      m.permissionCode = ms[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    Map.of("timezone", "Asia/Shanghai", "companyName", "StageCue 舞台提示", "maxRecords", "1000")
        .forEach(
            (k, v) -> {
              var s = new SystemSetting();
              s.code = k;
              s.value = v;
              db.save(s);
            });
    String[][] ks = {
      {"LIGHT", "灯光", "Lighting"},
      {"SOUND", "声音", "Sound"},
      {"STAGE", "舞台", "Stage"},
      {"VIDEO", "视频", "Video"}
    };
    for (var row : ks) {
      var v = new DictionaryEntry();
      v.type = "cue";
      v.code = row[0];
      v.name = row[1];
      v.nameEn = row[2];
      db.save(v);
    }
  }

  private AccessRole role(String name, String scope, Set<String> ps) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(ps);
    return db.save(r);
  }
}
