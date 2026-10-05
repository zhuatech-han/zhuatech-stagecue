// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.stagecue;

import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 场次业务与有限管理接口，每条入口由服务校验真实权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final StageService stage;
  final AdminService admin;
  final AccessService access;
  final Store db;

  public ApiController(StageService stage, AdminService admin, AccessService access, Store db) {
    this.stage = stage;
    this.admin = admin;
    this.access = access;
    this.db = db;
  }

  /** 有限表单目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return stage.options();
  }

  /** 授权业务分页。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{type:productions|books|runs}")
  public Object list(
      @PathVariable String type,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return stage.list(type, search, status, page, size, sort);
  }

  /** 完整授权业务详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{type:productions|books|runs}/{id}")
  public Object detail(@PathVariable String type, @PathVariable Long id) {
    return stage.detail(type, id);
  }

  /** 草稿新建与版本化目录创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/{type:productions|books|runs|cues}")
  public Object create(@PathVariable String type, @RequestBody StageService.Input v) {
    return save(type, null, v);
  }

  /** 有限字段编辑，状态流转另行处理。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/{type:productions|books|runs|cues}/{id}")
  public Object edit(
      @PathVariable String type, @PathVariable Long id, @RequestBody StageService.Input v) {
    return save(type, id, v);
  }

  private Object save(String type, Long id, StageService.Input v) {
    return switch (type) {
      case "productions" -> stage.saveProduction(id, v);
      case "books" -> stage.saveBook(id, v);
      case "cues" -> stage.saveCue(id, v);
      case "runs" -> stage.saveRun(id, v);
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 审批、执行、暂停处理与复盘命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/{type:books|runs|executions|holds}/{id}/commands/{action}")
  public Object command(
      @PathVariable String type,
      @PathVariable Long id,
      @PathVariable String action,
      @RequestBody StageService.Command v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    return switch (type) {
      case "books" -> stage.bookCommand(id, action, v);
      case "runs" -> stage.runCommand(id, action, v);
      case "executions" -> stage.executionCommand(id, action, v);
      case "holds" -> {
        if (!action.equals("resolve")) throw new Problem(400, "INVALID_ACTION");
        yield stage.resolveHold(id, v);
      }
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 无广告的授权JSON证据导出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/{type:productions|books|runs}/{id}/report.json")
  public ResponseEntity<Object> export(@PathVariable String type, @PathVariable Long id) {
    access.require("export");
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + type + "-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(stage.detail(type, id));
  }

  /** 授权真实统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return stage.dashboard();
  }

  /** 内部审计不向无权限岗位开放。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(
            e ->
                access.visible(e.departmentId)
                    && (!access.role().scope.equals("SELF")
                        || e.actor.equals(access.current().username)))
        .sorted(Comparator.comparing((AuditEvent e) -> e.id).reversed())
        .toList();
  }

  /** 有限管理目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object adminList(@PathVariable String type) {
    return admin.list(type);
  }

  /** 管理资源创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 管理资源更新。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminEdit(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 只删除未引用的管理资源，历史由外键保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
