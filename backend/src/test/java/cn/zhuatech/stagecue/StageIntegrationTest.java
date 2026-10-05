// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.stagecue;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 实际HTTP/JPA岗位、审批、冻结、暂停、并发与证据测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StageIntegrationTest {
  static final String PASSWORD = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("stagecue.admin-password", () -> PASSWORD);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate sql;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, ops, review, caller, operator, other, outside;
  long callerId, reviewId, operatorId, otherId, outDept;
  String suffix;

  String key() {
    return UUID.randomUUID().toString();
  }

  JsonNode body(Object value) {
    return json.valueToTree(value);
  }

  MockHttpSession login(String user) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(Map.of("username", user, "password", PASSWORD))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  MvcResult req(MockHttpSession who, String method, String path, Object value) throws Exception {
    var b =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    b.session(who).with(csrf());
    if (value != null) b.contentType("application/json").content(json.writeValueAsString(value));
    return mvc.perform(b).andReturn();
  }

  JsonNode ok(MockHttpSession who, String method, String path, Object value) throws Exception {
    var r = req(who, method, path, value);
    assertEquals(
        200, r.getResponse().getStatus(), path + " " + r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fail(MockHttpSession who, String method, String path, Object value, int status, String code)
      throws Exception {
    var r = req(who, method, path, value);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).path("code").asString());
  }

  Map<String, Object> cmd(JsonNode n) {
    return new HashMap<>(
        Map.of(
            "requestKey", key(), "version", n.path("version").asLong(), "note", "TEST verified"));
  }

  JsonNode action(MockHttpSession who, String type, JsonNode n, String action) throws Exception {
    return ok(
        who, "POST", "/" + type + "/" + n.path("id").asLong() + "/commands/" + action, cmd(n));
  }

  JsonNode user(String label, long role, long dept) throws Exception {
    return ok(
        admin,
        "POST",
        "/admin/users",
        Map.of(
            "username",
            label + suffix,
            "displayName",
            "TEST " + label,
            "password",
            PASSWORD,
            "roleId",
            role,
            "departmentId",
            dept,
            "enabled",
            true));
  }

  @BeforeAll
  void init() throws Exception {
    suffix = key().substring(0, 8);
    admin = login("admin");
    Map<String, Long> roles = new HashMap<>();
    for (var r : ok(admin, "GET", "/admin/roles", null))
      roles.put(r.path("name").asString(), r.path("id").asLong());
    user("ops", roles.get("制作协调"), 1);
    ops = login("ops" + suffix);
    reviewId = user("review", roles.get("独立监督"), 1).path("id").asLong();
    review = login("review" + suffix);
    callerId = user("caller", roles.get("提示员"), 1).path("id").asLong();
    caller = login("caller" + suffix);
    operatorId = user("operator", roles.get("技术操作员"), 1).path("id").asLong();
    operator = login("operator" + suffix);
    var all =
        ok(
            admin,
            "POST",
            "/admin/roles",
            Map.of(
                "name",
                "TEST OP ALL " + suffix,
                "scope",
                "ALL",
                "permissions",
                List.of("run.read", "cue.operate", "run.hold", "dashboard", "export")));
    otherId = user("other", all.path("id").asLong(), 1).path("id").asLong();
    other = login("other" + suffix);
    outDept =
        ok(admin, "POST", "/admin/departments", Map.of("name", "TEST outside " + suffix))
            .path("id")
            .asLong();
    user("outside", roles.get("制作协调"), outDept);
    outside = login("outside" + suffix);
  }

  class Fixture {
    JsonNode p, b, r, e1, e2;
    long runId, bookId;

    Fixture(boolean approved, boolean assigned) throws Exception {
      p =
          ok(
              ops,
              "POST",
              "/productions",
              Map.of(
                  "requestKey",
                  key(),
                  "reference",
                  "TEST-" + key(),
                  "name",
                  "TEST Production",
                  "departmentId",
                  1,
                  "enabled",
                  true));
      b =
          ok(
              ops,
              "POST",
              "/books",
              Map.of(
                  "requestKey",
                  key(),
                  "productionId",
                  p.path("id").asLong(),
                  "title",
                  "TEST Cue book"));
      bookId = b.path("id").asLong();
      cue(1, false);
      cue(2, true);
      b = record("books", bookId);
      if (!approved) return;
      b = action(ops, "books", b, "submit");
      b = action(review, "books", b, "approve");
      r = ok(ops, "POST", "/runs", runInput(b, "TEST-" + key()));
      runId = r.path("id").asLong();
      refresh();
      if (assigned) {
        e1 = assign(e1, operatorId);
        e2 = assign(e2, otherId);
        e1 = action(operator, "executions", e1, "receive");
        e2 = action(other, "executions", e2, "receive");
        refresh();
      }
    }

    Map<String, Object> runInput(JsonNode book, String ref) {
      return Map.of(
          "requestKey",
          key(),
          "reference",
          ref,
          "bookId",
          book.path("id").asLong(),
          "callerId",
          callerId,
          "supervisorId",
          reviewId,
          "location",
          "TEST Studio",
          "plannedAt",
          "2026-10-06T12:00:00Z",
          "mode",
          "REHEARSAL");
    }

    JsonNode cue(int seq, boolean optional) throws Exception {
      return ok(
          ops,
          "POST",
          "/cues",
          Map.of(
              "requestKey",
              key(),
              "bookId",
              bookId,
              "sequence",
              seq,
              "code",
              "LX" + seq,
              "kind",
              "LIGHT",
              "triggerText",
              "TEST landmark " + seq,
              "description",
              "TEST manual cue",
              "optional",
              optional,
              "enabled",
              true));
    }

    JsonNode assign(JsonNode e, long id) throws Exception {
      var c = cmd(e);
      c.put("operatorId", id);
      return ok(ops, "POST", "/executions/" + e.path("id").asLong() + "/commands/assign", c);
    }

    JsonNode record(String type, long id) throws Exception {
      return ok(admin, "GET", "/" + type + "/" + id, null).path("record");
    }

    void refresh() throws Exception {
      var d = ok(admin, "GET", "/runs/" + runId, null);
      r = d.path("record");
      e1 = d.path("executions").get(0);
      e2 = d.path("executions").get(1);
    }

    void start() throws Exception {
      refresh();
      r = action(caller, "runs", r, "start");
    }

    void ready() throws Exception {
      start();
      e1 = action(caller, "executions", e1, "standby");
      e1 = action(operator, "executions", e1, "ready");
    }

    void finish() throws Exception {
      ready();
      e1 = action(caller, "executions", e1, "call");
      e1 = action(operator, "executions", e1, "done");
      e2 = action(caller, "executions", e2, "skip");
      refresh();
      r = action(caller, "runs", r, "end");
    }
  }

  @Test
  void fullWorkflowAndIndependentReview() throws Exception {
    var f = new Fixture(true, true);
    f.finish();
    f.r = action(review, "runs", f.r, "close");
    assertEquals("CLOSED", f.r.path("status").asString());
    assertEquals("FINISHED", f.r.path("outcome").asString());
    var d = ok(review, "GET", "/runs/" + f.runId + "/report.json", null);
    assertTrue(d.path("events").size() > 8);
    assertFalse(d.toString().contains("passwordHash"));
  }

  @Test
  void allCrewMustReceive() throws Exception {
    var f = new Fixture(true, false);
    fail(caller, "POST", "/runs/" + f.runId + "/commands/start", cmd(f.r), 409, "CREW_NOT_READY");
  }

  @Test
  void callerCannotBeOperator() throws Exception {
    var f = new Fixture(true, false);
    var c = cmd(f.e1);
    c.put("operatorId", callerId);
    fail(
        admin,
        "POST",
        "/executions/" + f.e1.path("id").asLong() + "/commands/assign",
        c,
        409,
        "INELIGIBLE_ASSIGNMENT");
  }

  @Test
  void creatorCannotApproveOwnBook() throws Exception {
    var f = new Fixture(false, false);
    var b =
        ok(
            admin,
            "PUT",
            "/books/" + f.bookId,
            Map.of(
                "requestKey",
                key(),
                "version",
                f.b.path("version").asLong(),
                "productionId",
                f.p.path("id").asLong(),
                "title",
                "TEST revised by admin"));
    b = action(ops, "books", b, "submit");
    fail(
        admin,
        "POST",
        "/books/" + f.bookId + "/commands/approve",
        cmd(b),
        409,
        "INDEPENDENT_REVIEW_REQUIRED");
  }

  @Test
  void approvedCueCannotBeEdited() throws Exception {
    var f = new Fixture(true, true);
    var c = ok(ops, "GET", "/books/" + f.bookId, null).path("cues").get(0);
    fail(
        ops,
        "PUT",
        "/cues/" + c.path("id").asLong(),
        Map.of("requestKey", key(), "version", c.path("version").asLong(), "bookId", f.bookId),
        409,
        "INVALID_STATE");
  }

  @Test
  void newRevisionCopiesAndPinsExistingRun() throws Exception {
    var f = new Fixture(true, true);
    var n =
        ok(
            ops,
            "POST",
            "/books",
            Map.of(
                "requestKey",
                key(),
                "productionId",
                f.p.path("id").asLong(),
                "title",
                "TEST next version"));
    assertEquals(2, n.path("revision").asInt());
    var cues = ok(ops, "GET", "/books/" + n.path("id").asLong(), null).path("cues");
    assertEquals(2, cues.size());
    n = action(ops, "books", n, "submit");
    n = action(review, "books", n, "approve");
    fail(ops, "POST", "/runs", f.runInput(f.b, "TEST-" + key()), 409, "SUPERSEDED_BOOK");
    f.refresh();
    assertEquals(1, f.r.path("bookRevision").asInt());
    assertEquals(f.b.path("contentHash"), f.r.path("bookHash"));
    f.start();
  }

  @Test
  void oneOpenRevisionOnly() throws Exception {
    var f = new Fixture(false, false);
    fail(
        ops,
        "POST",
        "/books",
        Map.of(
            "requestKey",
            key(),
            "productionId",
            f.p.path("id").asLong(),
            "title",
            "TEST duplicate"),
        409,
        "OPEN_REVISION");
  }

  @Test
  void submitNeedsMandatoryCue() throws Exception {
    var p =
        ok(
            ops,
            "POST",
            "/productions",
            Map.of(
                "requestKey",
                key(),
                "reference",
                "TEST-" + key(),
                "name",
                "TEST empty",
                "departmentId",
                1,
                "enabled",
                true));
    var b =
        ok(
            ops,
            "POST",
            "/books",
            Map.of(
                "requestKey", key(), "productionId", p.path("id").asLong(), "title", "TEST empty"));
    fail(
        ops,
        "POST",
        "/books/" + b.path("id").asLong() + "/commands/submit",
        cmd(b),
        409,
        "MANDATORY_CUE_REQUIRED");
  }

  @Test
  void cueOrderCannotJump() throws Exception {
    var f = new Fixture(true, true);
    f.start();
    fail(
        caller,
        "POST",
        "/executions/" + f.e2.path("id").asLong() + "/commands/standby",
        cmd(f.e2),
        409,
        "CUE_OUT_OF_ORDER");
  }

  @Test
  void mandatoryCannotSkip() throws Exception {
    var f = new Fixture(true, true);
    f.start();
    fail(
        caller,
        "POST",
        "/executions/" + f.e1.path("id").asLong() + "/commands/skip",
        cmd(f.e1),
        409,
        "MANDATORY_CUE_CANNOT_SKIP");
  }

  @Test
  void readyRequiresStandby() throws Exception {
    var f = new Fixture(true, true);
    f.start();
    fail(
        operator,
        "POST",
        "/executions/" + f.e1.path("id").asLong() + "/commands/ready",
        cmd(f.e1),
        409,
        "INVALID_STATE");
  }

  @Test
  void callRequiresOperatorReady() throws Exception {
    var f = new Fixture(true, true);
    f.start();
    f.e1 = action(caller, "executions", f.e1, "standby");
    fail(
        caller,
        "POST",
        "/executions/" + f.e1.path("id").asLong() + "/commands/call",
        cmd(f.e1),
        409,
        "INVALID_STATE");
  }

  @Test
  void holdBlocksAndResumeInvalidatesReady() throws Exception {
    var f = new Fixture(true, true);
    f.ready();
    f.refresh();
    f.r = action(operator, "runs", f.r, "hold");
    fail(
        caller,
        "POST",
        "/executions/" + f.e1.path("id").asLong() + "/commands/call",
        cmd(f.e1),
        409,
        "INVALID_STATE");
    fail(caller, "POST", "/runs/" + f.runId + "/commands/resume", cmd(f.r), 409, "OPEN_HOLD");
    var h = ok(review, "GET", "/runs/" + f.runId, null).path("holds").get(0);
    action(review, "holds", h, "resolve");
    f.refresh();
    f.r = action(caller, "runs", f.r, "resume");
    f.refresh();
    assertEquals("PENDING", f.e1.path("status").asString());
    assertTrue(f.e1.path("readyAt").isNull());
    assertTrue(
        ok(caller, "GET", "/runs/" + f.runId, null)
            .path("events")
            .toString()
            .contains("RESET_AFTER_HOLD"));
  }

  @Test
  void calledCueCanReportDuringHold() throws Exception {
    var f = new Fixture(true, true);
    f.ready();
    f.e1 = action(caller, "executions", f.e1, "call");
    f.refresh();
    f.r = action(caller, "runs", f.r, "hold");
    f.e1 = action(operator, "executions", f.e1, "done");
    assertEquals("DONE", f.e1.path("status").asString());
  }

  @Test
  void onlyAssignedSupervisorResolves() throws Exception {
    var f = new Fixture(true, true);
    f.start();
    f.r = action(caller, "runs", f.r, "hold");
    var h = ok(admin, "GET", "/runs/" + f.runId, null).path("holds").get(0);
    fail(
        admin,
        "POST",
        "/holds/" + h.path("id").asLong() + "/commands/resolve",
        cmd(h),
        403,
        "ASSIGNED_SUPERVISOR_REQUIRED");
  }

  @Test
  void reassignClearsReceipt() throws Exception {
    var f = new Fixture(true, true);
    f.e1 = f.assign(f.e1, otherId);
    assertTrue(f.e1.path("receivedAt").isNull());
    f.refresh();
    fail(caller, "POST", "/runs/" + f.runId + "/commands/start", cmd(f.r), 409, "CREW_NOT_READY");
  }

  @Test
  void runEditClearsAllReceipts() throws Exception {
    var f = new Fixture(true, true);
    var c = new HashMap<>(f.runInput(f.b, f.r.path("reference").asString()));
    c.put("version", f.r.path("version").asLong());
    ok(ops, "PUT", "/runs/" + f.runId, c);
    f.refresh();
    assertTrue(f.e1.path("receivedAt").isNull());
    assertTrue(f.e2.path("receivedAt").isNull());
  }

  @Test
  void broadScopeDoesNotGrantOtherCueOrCaller() throws Exception {
    var f = new Fixture(true, true);
    var d = ok(other, "GET", "/runs/" + f.runId, null);
    assertEquals(1, d.path("executions").size());
    assertEquals(f.e2.path("id"), d.path("executions").get(0).path("id"));
    f.start();
    fail(
        admin,
        "POST",
        "/executions/" + f.e1.path("id").asLong() + "/commands/standby",
        cmd(f.e1),
        403,
        "ASSIGNED_CALLER_REQUIRED");
    fail(
        other,
        "POST",
        "/executions/" + f.e1.path("id").asLong() + "/commands/ready",
        cmd(f.e1),
        403,
        "OUT_OF_SCOPE");
  }

  @Test
  void crossDepartmentReadAndExportDenied() throws Exception {
    var f = new Fixture(true, true);
    fail(outside, "GET", "/runs/" + f.runId, null, 403, "OUT_OF_SCOPE");
    fail(outside, "GET", "/runs/" + f.runId + "/report.json", null, 403, "OUT_OF_SCOPE");
    var list = ok(outside, "GET", "/runs", null);
    assertEquals(0, list.path("total").asInt());
  }

  @Test
  void exactRetryReturnsHistoricalResponse() throws Exception {
    var f = new Fixture(true, true);
    f.start();
    var c = cmd(f.e1);
    var path = "/executions/" + f.e1.path("id").asLong() + "/commands/standby";
    var old = ok(caller, "POST", path, c);
    action(operator, "executions", old, "ready");
    assertEquals(old, ok(caller, "POST", path, c));
    c.put("note", "TEST different");
    fail(caller, "POST", path, c, 409, "REQUEST_KEY_REUSED");
  }

  @Test
  void staleVersionRejected() throws Exception {
    var f = new Fixture(true, true);
    f.start();
    var c = cmd(f.e1);
    c.put("version", 0);
    fail(
        caller,
        "POST",
        "/executions/" + f.e1.path("id").asLong() + "/commands/standby",
        c,
        409,
        "STALE_VERSION");
  }

  @Test
  void failedCommandDoesNotConsumeKey() throws Exception {
    var f = new Fixture(true, false);
    var c = cmd(f.r);
    String path = "/runs/" + f.runId + "/commands/start";
    fail(caller, "POST", path, c, 409, "CREW_NOT_READY");
    f.e1 = f.assign(f.e1, operatorId);
    f.e2 = f.assign(f.e2, otherId);
    action(operator, "executions", f.e1, "receive");
    action(other, "executions", f.e2, "receive");
    f.refresh();
    c.put("version", f.r.path("version").asLong());
    assertEquals("RUNNING", ok(caller, "POST", path, c).path("status").asString());
  }

  @Test
  void concurrentStandbyCommitsOnce() throws Exception {
    var f = new Fixture(true, true);
    f.start();
    var a = cmd(f.e1);
    var b = cmd(f.e1);
    var path = "/executions/" + f.e1.path("id").asLong() + "/commands/standby";
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> req(caller, "POST", path, a).getResponse().getStatus());
      var second = pool.submit(() -> req(caller, "POST", path, b).getResponse().getStatus());
      assertEquals(Set.of(200, 409), Set.of(first.get(), second.get()));
    }
    f.refresh();
    assertEquals("STANDBY", f.e1.path("status").asString());
  }

  @Test
  void cannotEndUnfinishedRun() throws Exception {
    var f = new Fixture(true, true);
    f.start();
    fail(caller, "POST", "/runs/" + f.runId + "/commands/end", cmd(f.r), 409, "UNFINISHED_CUES");
  }

  @Test
  void abortRetainsDistinctOutcomeAndNeedsReview() throws Exception {
    var f = new Fixture(true, true);
    f.start();
    f.r = action(caller, "runs", f.r, "abort");
    f.r = action(review, "runs", f.r, "close");
    assertEquals("ABORTED", f.r.path("outcome").asString());
    assertEquals("CLOSED", f.r.path("status").asString());
  }

  @Test
  void cannotAbortUnreportedCalledCue() throws Exception {
    var f = new Fixture(true, true);
    f.ready();
    f.e1 = action(caller, "executions", f.e1, "call");
    f.refresh();
    fail(
        caller,
        "POST",
        "/runs/" + f.runId + "/commands/abort",
        cmd(f.r),
        409,
        "EXECUTION_STILL_OPEN");
  }

  @Test
  void cancelOnlyPreparing() throws Exception {
    var f = new Fixture(true, true);
    f.r = action(caller, "runs", f.r, "cancel");
    assertEquals("CANCELLED", f.r.path("status").asString());
    fail(caller, "POST", "/runs/" + f.runId + "/commands/start", cmd(f.r), 409, "INVALID_STATE");
  }

  @Test
  void adminCannotCloseUnassignedRun() throws Exception {
    var f = new Fixture(true, true);
    f.finish();
    fail(
        admin,
        "POST",
        "/runs/" + f.runId + "/commands/close",
        cmd(f.r),
        403,
        "ASSIGNED_SUPERVISOR_REQUIRED");
  }

  @Test
  void duplicateSequenceIsDatabaseConflict() throws Exception {
    var f = new Fixture(false, false);
    fail(
        ops,
        "POST",
        "/cues",
        Map.of(
            "requestKey",
            key(),
            "bookId",
            f.bookId,
            "sequence",
            1,
            "code",
            "OTHER",
            "kind",
            "LIGHT",
            "triggerText",
            "TEST",
            "description",
            "TEST",
            "enabled",
            true),
        409,
        "CONFLICT");
  }

  @Test
  void identityNotChangedAndPasswordsNotListed() throws Exception {
    var f = new Fixture(true, true);
    var data = ok(admin, "GET", "/admin/users", null).toString();
    assertFalse(data.contains("passwordHash"));
    assertFalse(data.contains(PASSWORD));
    assertEquals(15, ok(admin, "GET", "/admin/permissions", null).size());
    assertEquals(12, ok(admin, "GET", "/admin/menus", null).size());
  }

  @Test
  void unauthorizedAndCsrfDenied() throws Exception {
    assertEquals(401, mvc.perform(get("/api/runs")).andReturn().getResponse().getStatus());
    assertEquals(
        403,
        mvc.perform(post("/api/runs").session(ops).contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
    fail(operator, "GET", "/admin/users", null, 403, "FORBIDDEN");
  }

  @Test
  void lastAdminProtected() throws Exception {
    var a = ok(admin, "GET", "/admin/users", null).get(0);
    fail(
        admin,
        "PUT",
        "/admin/users/" + a.path("id").asLong(),
        Map.of(
            "username",
            "admin",
            "displayName",
            "TEST admin",
            "roleId",
            a.path("roleId").asLong(),
            "departmentId",
            1,
            "enabled",
            false),
        409,
        "LAST_ADMIN");
    assertEquals("admin", ok(admin, "GET", "/auth/me", null).path("username").asString());
  }

  @Test
  void disableAssignedOperatorBlocksStart() throws Exception {
    var f = new Fixture(true, true);
    var r = ok(admin, "GET", "/admin/roles", null);
    long role = 0;
    for (var row : r)
      if (row.path("name").asString().equals("技术操作员")) role = row.path("id").asLong();
    var a = user("temp" + key().substring(0, 5), role, 1);
    long id = a.path("id").asLong();
    var name = a.path("username").asString();
    var s = login(name);
    f.e1 = f.assign(f.e1, id);
    action(s, "executions", f.e1, "receive");
    ok(
        admin,
        "PUT",
        "/admin/users/" + id,
        Map.of(
            "username",
            name,
            "displayName",
            "TEST disabled",
            "roleId",
            role,
            "departmentId",
            1,
            "enabled",
            false));
    f.refresh();
    fail(
        caller,
        "POST",
        "/runs/" + f.runId + "/commands/start",
        cmd(f.r),
        409,
        "INELIGIBLE_ASSIGNMENT");
    fail(s, "GET", "/runs/" + f.runId, null, 401, "UNAUTHENTICATED");
  }

  @Test
  void operatorNamesOnlyIncludeVisibleAssignments() throws Exception {
    var f = new Fixture(true, true);
    var o = ok(operator, "GET", "/options", null);
    Set<Long> ids = new HashSet<>();
    for (var p : o.path("people")) ids.add(p.path("id").asLong());
    assertTrue(ids.contains(callerId) && ids.contains(reviewId) && ids.contains(operatorId));
    assertFalse(ids.contains(otherId));
    assertFalse(o.path("people").toString().contains("permissions"));
  }

  @Test
  void displaySettingIsActuallyReturned() throws Exception {
    JsonNode setting = null;
    for (var s : ok(admin, "GET", "/admin/settings", null))
      if (s.path("code").asString().equals("companyName")) setting = s;
    assertNotNull(setting);
    String before = setting.path("value").asString();
    ok(
        admin,
        "PUT",
        "/admin/settings/" + setting.path("id").asLong(),
        Map.of("value", "TEST display name"));
    assertEquals(
        "TEST display name", ok(operator, "GET", "/options", null).path("companyName").asString());
    ok(admin, "PUT", "/admin/settings/" + setting.path("id").asLong(), Map.of("value", before));
  }
}
