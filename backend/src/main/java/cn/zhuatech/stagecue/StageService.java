// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.stagecue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 提示本冻结、场次快照、指派执行与暂停恢复的事务边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class StageService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final ObjectMapper json;

  public StageService(Store db, AccessService access, Clock clock, ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.clock = clock;
    this.json = json;
  }

  /** 明确草稿字段，不接收状态或事实时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(
      String requestKey,
      Long version,
      String reference,
      String name,
      Long departmentId,
      Boolean enabled,
      Long productionId,
      String title,
      Long bookId,
      Integer sequence,
      String code,
      String kind,
      String triggerText,
      String description,
      Boolean optional,
      Long callerId,
      Long supervisorId,
      String location,
      Instant plannedAt,
      String mode) {}

  /** 所有动作绑定版本与完整UUID载荷，事实时刻由服务器生成。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(String requestKey, Long version, String note, Long operatorId) {}

  private static final Set<String> EDITABLE = Set.of("DRAFT", "RETURNED");
  private static final Set<String> TERMINAL = Set.of("DONE", "SKIPPED");
  private static final Set<String> RUN_FINAL = Set.of("CLOSED", "CANCELLED", "ABORTED", "ENDED");

  private void check(boolean ok, String code) {
    if (!ok) throw new Problem(409, code);
  }

  private String text(String s, int n) {
    return AdminService.text(s, n);
  }

  private Instant now() {
    return BusinessTime.now(clock);
  }

  private Long who() {
    return access.current().id;
  }

  private void lock() {
    db.lock(Department.class, 1L);
    var a = access.current();
    db.refresh(a);
    db.refresh(db.get(AccessRole.class, a.roleId));
    access.current();
  }

  private void version(long actual, Long input) {
    check(input != null && input == actual, "STALE_VERSION");
  }

  private boolean scope(Long dept, Long creator) {
    return access.visible(dept)
        && (!access.role().scope.equals("SELF") || Objects.equals(who(), creator));
  }

  private boolean operatorOnly() {
    var p = access.role().permissions;
    return p.contains("cue.operate")
        && Collections.disjoint(
            p, Set.of("book.write", "book.review", "run.write", "run.call", "run.review", "admin"));
  }

  private boolean productionVisible(Production p) {
    return !operatorOnly() && scope(p.departmentId, p.createdBy);
  }

  private Production production(Long id) {
    var p = db.get(Production.class, id);
    if (!productionVisible(p)) throw new Problem(403, "OUT_OF_SCOPE");
    return p;
  }

  private CueBook book(Long id) {
    var b = db.get(CueBook.class, id);
    production(b.productionId);
    return b;
  }

  private List<CueDefinition> cues(Long id) {
    return db.query(
        CueDefinition.class, "from CueDefinition where bookId=?1 order by sequence", id);
  }

  private List<CueExecution> executions(Long id) {
    return db.query(CueExecution.class, "from CueExecution where runId=?1 order by sequence", id);
  }

  private List<RunHold> holds(Long id) {
    return db.query(RunHold.class, "from RunHold where runId=?1 order by id", id);
  }

  private boolean runVisible(ShowRun r) {
    return Objects.equals(who(), r.callerId)
        || Objects.equals(who(), r.supervisorId)
        || executions(r.id).stream().anyMatch(e -> Objects.equals(who(), e.operatorId))
        || (!operatorOnly() && scope(r.departmentId, r.createdBy));
  }

  private ShowRun run(Long id) {
    var r = db.get(ShowRun.class, id);
    if (!runVisible(r)) throw new Problem(403, "OUT_OF_SCOPE");
    return r;
  }

  private CueExecution execution(Long id) {
    var e = db.get(CueExecution.class, id);
    run(e.runId);
    if (operatorOnly() && !Objects.equals(who(), e.operatorId))
      throw new Problem(403, "OUT_OF_SCOPE");
    return e;
  }

  private void caller(ShowRun r) {
    access.require("run.call");
    if (!Objects.equals(who(), r.callerId)) throw new Problem(403, "ASSIGNED_CALLER_REQUIRED");
  }

  private void supervisor(ShowRun r) {
    access.require("run.review");
    if (!Objects.equals(who(), r.supervisorId))
      throw new Problem(403, "ASSIGNED_SUPERVISOR_REQUIRED");
  }

  private void operator(CueExecution e) {
    access.require("cue.operate");
    if (!Objects.equals(who(), e.operatorId)) throw new Problem(403, "ASSIGNED_OPERATOR_REQUIRED");
  }

  private void eligible(Long id, String permission) {
    var a = db.get(Account.class, id);
    check(
        a.enabled && db.get(AccessRole.class, a.roleId).permissions.contains(permission),
        "INELIGIBLE_ASSIGNMENT");
  }

  private void editor(CueBook b) {
    if (db.query(BookEditor.class, "from BookEditor where bookId=?1 and actorId=?2", b.id, who())
        .isEmpty()) {
      var e = new BookEditor();
      e.bookId = b.id;
      e.actorId = who();
      db.save(e);
    }
  }

  private void independent(CueBook b) {
    check(
        db.query(BookEditor.class, "from BookEditor where bookId=?1 and actorId=?2", b.id, who())
            .isEmpty(),
        "INDEPENDENT_REVIEW_REQUIRED");
  }

  private void openHolds(ShowRun r) {
    check(holds(r.id).stream().noneMatch(h -> h.status.equals("OPEN")), "OPEN_HOLD");
  }

  private void next(ShowRun r, CueExecution e) {
    var first = executions(r.id).stream().filter(x -> !TERMINAL.contains(x.status)).findFirst();
    check(first.isPresent() && Objects.equals(first.get().id, e.id), "CUE_OUT_OF_ORDER");
  }

  private void limit(Class<?> cls) {
    int max =
        Integer.parseInt(
            db.query(SystemSetting.class, "from SystemSetting where code=?1", "maxRecords")
                .getFirst()
                .value);
    check(db.all(cls).size() < max, "RECORD_LIMIT");
  }

  private String encode(Object value) {
    return json.writeValueAsString(value);
  }

  private String hash(Object value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(encode(value).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private Object decode(String value) {
    return json.readValue(value, Map.class);
  }

  private Object write(String key, Object payload, Supplier<Object> action) {
    try {
      if (key == null || !UUID.fromString(key).toString().equals(key))
        throw new Problem(400, "INVALID_REQUEST_KEY");
    } catch (IllegalArgumentException e) {
      throw new Problem(400, "INVALID_REQUEST_KEY");
    }
    String fp = hash(List.of(who(), payload));
    var old = db.query(CommandRecord.class, "from CommandRecord where requestKey=?1", key);
    if (!old.isEmpty()) {
      check(old.getFirst().fingerprint.equals(fp), "REQUEST_KEY_REUSED");
      return decode(old.getFirst().responseJson);
    }
    Object result = action.get();
    db.flush();
    var c = new CommandRecord();
    c.requestKey = key;
    c.fingerprint = fp;
    c.responseJson = encode(result);
    db.save(c);
    return result;
  }

  private void event(String type, Long id, String action, String note, Object value, Long dept) {
    var e = new BusinessEvent();
    e.objectType = type;
    e.objectId = id;
    e.actorId = who();
    e.action = action;
    e.note = note;
    e.snapshot = encode(value);
    e.createdAt = now();
    db.save(e);
    access.audit(type + "_" + action, id, dept);
  }

  private List<BusinessEvent> events(String type, Long id) {
    return db.query(
        BusinessEvent.class,
        "from BusinessEvent where objectType=?1 and objectId=?2 order by id",
        type,
        id);
  }

  /** 创建或维护制作目录，编号与归属冻结，业务记录不删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveProduction(Long id, Input v) {
    lock();
    access.require("production.write");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var prior = id == null ? null : production(id);
    access.department(v.departmentId);
    return write(
        v.requestKey,
        List.of("production", id == null ? 0 : id, v),
        () -> {
          var p = prior == null ? new Production() : prior;
          if (prior == null) {
            limit(Production.class);
            p.reference = text(v.reference, 60);
            p.departmentId = db.get(Department.class, v.departmentId).id;
            p.createdBy = who();
          } else {
            version(p.version, v.version);
            check(
                Objects.equals(p.reference, v.reference)
                    && Objects.equals(p.departmentId, v.departmentId),
                "IMMUTABLE_IDENTITY");
            p.version++;
          }
          p.name = text(v.name, 120);
          p.enabled = Boolean.TRUE.equals(v.enabled);
          if (prior == null) db.save(p);
          event("productions", p.id, "SAVE", "", p, p.departmentId);
          return p;
        });
  }

  /** 建立下一版草稿并复制上版提示，保留上版和所有已有场次。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveBook(Long id, Input v) {
    lock();
    access.require("book.write");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var p = production(v.productionId);
    check(p.enabled, "DISABLED_RESOURCE");
    var prior = id == null ? null : book(id);
    return write(
        v.requestKey,
        List.of("book", id == null ? 0 : id, v),
        () -> {
          var b = prior == null ? new CueBook() : prior;
          if (prior == null) {
            limit(CueBook.class);
            var siblings =
                db.query(
                    CueBook.class,
                    "from CueBook where productionId=?1 order by revision desc",
                    p.id);
            check(
                siblings.stream()
                    .noneMatch(x -> Set.of("DRAFT", "RETURNED", "SUBMITTED").contains(x.status)),
                "OPEN_REVISION");
            b.productionId = p.id;
            b.revision = siblings.isEmpty() ? 1 : siblings.getFirst().revision + 1;
            b.createdBy = who();
            b.createdAt = now();
            b.status = "DRAFT";
            b.title = text(v.title, 120);
            db.save(b);
            var approved = siblings.stream().filter(x -> x.status.equals("APPROVED")).findFirst();
            if (approved.isPresent())
              for (var old : cues(approved.get().id)) {
                if (!old.enabled) continue;
                var c = new CueDefinition();
                c.bookId = b.id;
                c.sequence = old.sequence;
                c.code = old.code;
                c.kind = old.kind;
                c.triggerText = old.triggerText;
                c.description = old.description;
                c.optional = old.optional;
                c.enabled = true;
                db.save(c);
              }
          } else {
            version(b.version, v.version);
            check(Objects.equals(b.productionId, p.id), "IMMUTABLE_IDENTITY");
            check(EDITABLE.contains(b.status), "INVALID_STATE");
            b.title = text(v.title, 120);
            b.version++;
          }
          editor(b);
          event("books", b.id, "SAVE", "", b, p.departmentId);
          return b;
        });
  }

  /** 修改未批准提示；排序和编码均有数据库唯一约束。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveCue(Long id, Input v) {
    lock();
    access.require("book.write");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var b = book(v.bookId);
    var prior = id == null ? null : db.get(CueDefinition.class, id);
    return write(
        v.requestKey,
        List.of("cue", id == null ? 0 : id, v),
        () -> {
          check(EDITABLE.contains(b.status), "INVALID_STATE");
          var c = prior == null ? new CueDefinition() : prior;
          if (prior == null) {
            check(cues(b.id).size() < 200, "CUE_LIMIT");
            c.bookId = b.id;
          } else {
            version(c.version, v.version);
            check(Objects.equals(c.bookId, b.id), "IMMUTABLE_IDENTITY");
            c.version++;
          }
          check(v.sequence != null && v.sequence >= 1 && v.sequence <= 9999, "INVALID_SEQUENCE");
          c.sequence = v.sequence;
          c.code = text(v.code, 60);
          c.kind = text(v.kind, 60);
          check(Set.of("LIGHT", "SOUND", "STAGE", "VIDEO").contains(c.kind), "INVALID_KIND");
          c.triggerText = text(v.triggerText, 300);
          c.description = text(v.description, 1000);
          c.optional = Boolean.TRUE.equals(v.optional);
          c.enabled = Boolean.TRUE.equals(v.enabled);
          if (prior == null) db.save(c);
          b.version++;
          editor(b);
          event("books", b.id, "CUE_SAVE", "", c, production(b.productionId).departmentId);
          return c;
        });
  }

  /** 提交、退回、撤回、独立批准或作废当前提示本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object bookCommand(Long id, String action, Command v) {
    lock();
    var b = book(id);
    access.require(Set.of("approve", "reject").contains(action) ? "book.review" : "book.write");
    return write(
        v.requestKey,
        List.of("book-action", id, action, v),
        () -> {
          version(b.version, v.version);
          String note = text(v.note, 1000);
          switch (action) {
            case "submit" -> {
              check(EDITABLE.contains(b.status), "INVALID_STATE");
              var active = cues(id).stream().filter(c -> c.enabled).toList();
              check(
                  !active.isEmpty() && active.stream().anyMatch(c -> !c.optional),
                  "MANDATORY_CUE_REQUIRED");
              b.status = "SUBMITTED";
            }
            case "approve" -> {
              check(b.status.equals("SUBMITTED"), "INVALID_STATE");
              independent(b);
              b.status = "APPROVED";
              b.approvedBy = who();
              b.approvedAt = now();
              b.contentHash = hash(cues(id).stream().filter(c -> c.enabled).toList());
            }
            case "reject" -> {
              check(b.status.equals("SUBMITTED"), "INVALID_STATE");
              independent(b);
              b.status = "RETURNED";
            }
            case "withdraw" -> {
              check(b.status.equals("SUBMITTED"), "INVALID_STATE");
              check(Objects.equals(b.createdBy, who()), "CREATOR_REQUIRED");
              b.status = "DRAFT";
            }
            case "cancel" -> {
              check(EDITABLE.contains(b.status) || b.status.equals("SUBMITTED"), "INVALID_STATE");
              check(Objects.equals(b.createdBy, who()), "CREATOR_REQUIRED");
              b.status = "CANCELLED";
            }
            default -> throw new Problem(400, "INVALID_ACTION");
          }
          b.version++;
          event("books", id, action, note, b, production(b.productionId).departmentId);
          return b;
        });
  }

  /** 场次只采用当时最新已批准版本；复制提示文本与审批摘要。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveRun(Long id, Input v) {
    lock();
    access.require("run.write");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var prior = id == null ? null : run(id);
    if (prior != null && !scope(prior.departmentId, prior.createdBy))
      throw new Problem(403, "OUT_OF_SCOPE");
    var b = book(v.bookId);
    var p = production(b.productionId);
    check(p.enabled, "DISABLED_RESOURCE");
    return write(
        v.requestKey,
        List.of("run", id == null ? 0 : id, v),
        () -> {
          var r = prior == null ? new ShowRun() : prior;
          eligible(v.callerId, "run.call");
          eligible(v.supervisorId, "run.review");
          check(!Objects.equals(v.callerId, v.supervisorId), "INDEPENDENT_REVIEW_REQUIRED");
          if (prior == null) {
            limit(ShowRun.class);
            check(b.status.equals("APPROVED"), "INVALID_STATE");
            check(
                db.query(
                        CueBook.class,
                        "from CueBook where productionId=?1 and status='APPROVED' and revision>?2",
                        p.id,
                        b.revision)
                    .isEmpty(),
                "SUPERSEDED_BOOK");
            r.reference = text(v.reference, 60);
            r.bookId = b.id;
            r.productionId = p.id;
            r.departmentId = p.departmentId;
            r.createdBy = who();
            r.productionName = p.name;
            r.bookTitle = b.title;
            r.bookRevision = b.revision;
            r.bookHash = b.contentHash;
            r.status = "PREPARING";
          } else {
            version(r.version, v.version);
            check(r.status.equals("PREPARING"), "INVALID_STATE");
            check(
                Objects.equals(r.reference, v.reference) && Objects.equals(r.bookId, b.id),
                "IMMUTABLE_IDENTITY");
            for (var e : executions(id)) {
              check(
                  !Objects.equals(e.operatorId, v.callerId)
                      && !Objects.equals(e.operatorId, v.supervisorId),
                  "INDEPENDENT_REVIEW_REQUIRED");
              e.receivedAt = null;
              e.version++;
            }
            r.version++;
          }
          r.callerId = v.callerId;
          r.supervisorId = v.supervisorId;
          r.location = text(v.location, 200);
          r.plannedAt = BusinessTime.planned(v.plannedAt);
          check(
              Set.of("REHEARSAL", "PERFORMANCE").contains(v.mode == null ? "" : v.mode),
              "INVALID_MODE");
          r.mode = v.mode;
          if (prior == null) {
            db.save(r);
            for (var c : cues(b.id)) {
              if (!c.enabled) continue;
              var e = new CueExecution();
              e.runId = r.id;
              e.sourceCueId = c.id;
              e.sequence = c.sequence;
              e.code = c.code;
              e.kind = c.kind;
              e.triggerText = c.triggerText;
              e.description = c.description;
              e.optional = c.optional;
              e.status = "PENDING";
              e.note = "";
              db.save(e);
            }
          }
          event("runs", r.id, "SAVE", "", r, r.departmentId);
          return r;
        });
  }

  /** 指派与收悉受各自岗位约束，待命和执行严格按场次顺序推进。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object executionCommand(Long id, String action, Command v) {
    lock();
    var e = execution(id);
    var r = run(e.runId);
    if (Set.of("assign").contains(action)) {
      access.require("run.write");
      if (!scope(r.departmentId, r.createdBy)) throw new Problem(403, "OUT_OF_SCOPE");
    } else if (Set.of("receive", "ready", "done").contains(action)) operator(e);
    else caller(r);
    return write(
        v.requestKey,
        List.of("execution-action", id, action, v),
        () -> {
          version(e.version, v.version);
          String note = text(v.note, 1000);
          switch (action) {
            case "assign" -> {
              check(r.status.equals("PREPARING"), "INVALID_STATE");
              eligible(v.operatorId, "cue.operate");
              check(
                  !Objects.equals(v.operatorId, r.callerId)
                      && !Objects.equals(v.operatorId, r.supervisorId),
                  "INDEPENDENT_REVIEW_REQUIRED");
              e.operatorId = v.operatorId;
              e.receivedAt = null;
            }
            case "receive" -> {
              check(r.status.equals("PREPARING"), "INVALID_STATE");
              check(e.receivedAt == null, "ALREADY_RECEIVED");
              e.receivedAt = now();
            }
            case "standby" -> {
              check(r.status.equals("RUNNING"), "INVALID_STATE");
              next(r, e);
              check(e.status.equals("PENDING"), "INVALID_STATE");
              eligible(e.operatorId, "cue.operate");
              e.status = "STANDBY";
              e.standbyAt = now();
            }
            case "ready" -> {
              check(r.status.equals("RUNNING") && e.status.equals("STANDBY"), "INVALID_STATE");
              next(r, e);
              e.status = "READY";
              e.readyAt = now();
            }
            case "call" -> {
              check(r.status.equals("RUNNING") && e.status.equals("READY"), "INVALID_STATE");
              next(r, e);
              eligible(e.operatorId, "cue.operate");
              e.status = "CALLED";
              e.calledAt = now();
            }
            case "done" -> {
              check(
                  Set.of("RUNNING", "HELD").contains(r.status) && e.status.equals("CALLED"),
                  "INVALID_STATE");
              e.status = "DONE";
              e.doneAt = now();
            }
            case "skip" -> {
              check(r.status.equals("RUNNING"), "INVALID_STATE");
              next(r, e);
              check(
                  e.optional && Set.of("PENDING", "STANDBY", "READY").contains(e.status),
                  "MANDATORY_CUE_CANNOT_SKIP");
              e.status = "SKIPPED";
            }
            default -> throw new Problem(400, "INVALID_ACTION");
          }
          e.note = note;
          e.version++;
          r.version++;
          event("executions", id, action, note, e, r.departmentId);
          return e;
        });
  }

  /** 开始、暂停、恢复、结束及独立复盘均是人工台账动作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object runCommand(Long id, String action, Command v) {
    lock();
    var r = run(id);
    if (action.equals("close")) supervisor(r);
    else if (action.equals("hold")) {
      access.require("run.hold");
      check(
          Objects.equals(who(), r.callerId)
              || executions(id).stream().anyMatch(e -> Objects.equals(who(), e.operatorId)),
          "ASSIGNED_MEMBER_REQUIRED");
    } else caller(r);
    return write(
        v.requestKey,
        List.of("run-action", id, action, v),
        () -> {
          version(r.version, v.version);
          String note = text(v.note, 1000);
          switch (action) {
            case "start" -> {
              check(r.status.equals("PREPARING"), "INVALID_STATE");
              eligible(r.supervisorId, "run.review");
              for (var e : executions(id)) {
                check(e.operatorId != null && e.receivedAt != null, "CREW_NOT_READY");
                eligible(e.operatorId, "cue.operate");
              }
              r.status = "RUNNING";
              r.startedAt = now();
            }
            case "hold" -> {
              check(r.status.equals("RUNNING"), "INVALID_STATE");
              var h = new RunHold();
              h.runId = id;
              h.raisedBy = who();
              h.reason = note;
              h.raisedAt = now();
              h.status = "OPEN";
              db.save(h);
              r.status = "HELD";
              event("holds", h.id, "RAISE", note, h, r.departmentId);
            }
            case "resume" -> {
              check(r.status.equals("HELD"), "INVALID_STATE");
              openHolds(r);
              for (var e : executions(id)) {
                if (Set.of("STANDBY", "READY").contains(e.status)) {
                  e.status = "PENDING";
                  e.standbyAt = null;
                  e.readyAt = null;
                  e.version++;
                  event("executions", e.id, "RESET_AFTER_HOLD", note, e, r.departmentId);
                }
              }
              r.status = "RUNNING";
            }
            case "end" -> {
              check(r.status.equals("RUNNING"), "INVALID_STATE");
              openHolds(r);
              check(
                  executions(id).stream().allMatch(e -> TERMINAL.contains(e.status)),
                  "UNFINISHED_CUES");
              r.status = "ENDED";
              r.outcome = "FINISHED";
              r.endedAt = now();
            }
            case "abort" -> {
              check(Set.of("RUNNING", "HELD").contains(r.status), "INVALID_STATE");
              openHolds(r);
              check(
                  executions(id).stream().noneMatch(e -> e.status.equals("CALLED")),
                  "EXECUTION_STILL_OPEN");
              r.status = "ABORTED";
              r.outcome = "ABORTED";
              r.endedAt = now();
            }
            case "cancel" -> {
              check(r.status.equals("PREPARING"), "INVALID_STATE");
              r.status = "CANCELLED";
              r.outcome = "CANCELLED";
            }
            case "close" -> {
              check(Set.of("ENDED", "ABORTED").contains(r.status), "INVALID_STATE");
              openHolds(r);
              r.status = "CLOSED";
              r.closedAt = now();
            }
            default -> throw new Problem(400, "INVALID_ACTION");
          }
          r.version++;
          event("runs", id, action, note, r, r.departmentId);
          return r;
        });
  }

  /** 指定独立监督员核实暂停处理，不能代替现场安全程序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object resolveHold(Long id, Command v) {
    lock();
    var h = db.get(RunHold.class, id);
    var r = run(h.runId);
    supervisor(r);
    return write(
        v.requestKey,
        List.of("hold-resolve", id, v),
        () -> {
          version(h.version, v.version);
          check(h.status.equals("OPEN") && r.status.equals("HELD"), "INVALID_STATE");
          check(!Objects.equals(h.raisedBy, who()), "INDEPENDENT_REVIEW_REQUIRED");
          h.resolution = text(v.note, 1000);
          h.status = "RESOLVED";
          h.resolvedBy = who();
          h.resolvedAt = now();
          h.version++;
          r.version++;
          event("holds", id, "RESOLVE", h.resolution, h, r.departmentId);
          return h;
        });
  }

  /** 详情按真实授权过滤操作证据；执行岗位只看本人提示。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(String type, Long id) {
    access.require(
        type.equals("productions")
            ? "production.read"
            : type.equals("books") ? "book.read" : "run.read");
    return switch (type) {
      case "productions" -> Map.of("record", production(id), "events", events(type, id));
      case "books" ->
          Map.of(
              "record",
              book(id),
              "cues",
              cues(id),
              "editors",
              db.query(BookEditor.class, "from BookEditor where bookId=?1", id),
              "events",
              events(type, id));
      case "runs" -> {
        var r = run(id);
        var es =
            executions(id).stream()
                .filter(e -> !operatorOnly() || Objects.equals(e.operatorId, who()))
                .toList();
        var ev = new ArrayList<BusinessEvent>(events(type, id));
        for (var e : es) ev.addAll(events("executions", e.id));
        for (var h : holds(id)) ev.addAll(events("holds", h.id));
        ev.sort(Comparator.comparing(e -> e.id));
        yield Map.of("record", r, "executions", es, "holds", holds(id), "events", ev);
      }
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 有界授权列表，筛选与分页不会提供无权记录数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String type, String search, String status, int page, int size, String sort) {
    if (page < 0
        || page > 10000
        || size < 1
        || size > 100
        || search.length() > 120
        || !Set.of("newest", "oldest").contains(sort)) throw new Problem(400, "INVALID_INPUT");
    String q = search.toLowerCase(Locale.ROOT);
    List<?> data;
    switch (type) {
      case "productions" -> {
        access.require("production.read");
        data =
            db.all(Production.class).stream()
                .filter(this::productionVisible)
                .filter(p -> (p.name + " " + p.reference).toLowerCase(Locale.ROOT).contains(q))
                .toList();
      }
      case "books" -> {
        access.require("book.read");
        data =
            db.all(CueBook.class).stream()
                .filter(b -> productionVisible(db.get(Production.class, b.productionId)))
                .filter(
                    b ->
                        b.title.toLowerCase(Locale.ROOT).contains(q)
                            && (status.isBlank() || b.status.equals(status)))
                .toList();
      }
      case "runs" -> {
        access.require("run.read");
        data =
            db.all(ShowRun.class).stream()
                .filter(this::runVisible)
                .filter(
                    r ->
                        (r.reference + " " + r.productionName + " " + r.location)
                                .toLowerCase(Locale.ROOT)
                                .contains(q)
                            && (status.isBlank() || r.status.equals(status)))
                .toList();
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    var rows = new ArrayList<>(data);
    if (sort.equals("newest")) Collections.reverse(rows);
    long start = (long) page * size;
    return Map.of(
        "content",
        rows.subList((int) Math.min(start, rows.size()), (int) Math.min(start + size, rows.size())),
        "total",
        rows.size());
  }

  /** 表单目录不返回密码、散列或无权业务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.current();
    var result = new LinkedHashMap<String, Object>();
    result.put(
        "companyName",
        db.query(SystemSetting.class, "from SystemSetting where code=?1", "companyName")
            .getFirst()
            .value);
    result.put(
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList());
    result.put("kinds", db.all(DictionaryEntry.class));
    result.put(
        "modes",
        List.of(
            Map.of("code", "REHEARSAL", "name", "排练", "nameEn", "Rehearsal"),
            Map.of("code", "PERFORMANCE", "name", "正式场次", "nameEn", "Performance")));
    if (!operatorOnly()) {
      result.put(
          "productions",
          db.all(Production.class).stream().filter(this::productionVisible).toList());
      result.put(
          "books",
          db.all(CueBook.class).stream()
              .filter(b -> productionVisible(db.get(Production.class, b.productionId)))
              .toList());
      if (access.role().permissions.contains("run.write"))
        result.put(
            "people",
            db.all(Account.class).stream()
                .filter(a -> a.enabled)
                .map(
                    a ->
                        Map.of(
                            "id",
                            a.id,
                            "name",
                            a.displayName,
                            "departmentId",
                            a.departmentId,
                            "permissions",
                            db.get(AccessRole.class, a.roleId).permissions))
                .toList());
    }
    if (!access.role().permissions.contains("run.write")) {
      var people = new HashSet<Long>();
      people.add(who());
      if (access.role().permissions.contains("run.read"))
        for (var r : db.all(ShowRun.class)) {
          if (!runVisible(r)) continue;
          people.add(r.callerId);
          people.add(r.supervisorId);
          for (var e : executions(r.id))
            if (e.operatorId != null && (!operatorOnly() || Objects.equals(who(), e.operatorId)))
              people.add(e.operatorId);
        }
      result.put(
          "people",
          db.all(Account.class).stream()
              .filter(a -> people.contains(a.id))
              .map(a -> Map.of("id", a.id, "name", a.displayName))
              .toList());
    }
    return result;
  }

  /** 指标只统计可见场次和提示，数据为空时不制造演示数字。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var rows =
        access.role().permissions.contains("run.read")
            ? db.all(ShowRun.class).stream().filter(this::runVisible).toList()
            : List.<ShowRun>of();
    Map<String, Long> states = new TreeMap<>();
    long done = 0, total = 0;
    for (var r : rows) {
      states.merge(r.status, 1L, Long::sum);
      for (var e : executions(r.id)) {
        if (operatorOnly() && !Objects.equals(who(), e.operatorId)) continue;
        total++;
        if (e.status.equals("DONE")) done++;
      }
    }
    return Map.of(
        "runs",
        rows.size(),
        "states",
        states,
        "cueTotal",
        total,
        "cueDone",
        done,
        "held",
        rows.stream().filter(r -> r.status.equals("HELD")).count());
  }
}
