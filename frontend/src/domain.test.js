// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
import { actions, payload, localTime } from "./domain.js";
const me = {
  id: 1,
  permissions: [
    "book.write",
    "book.review",
    "run.write",
    "run.call",
    "run.review",
    "cue.operate",
    "run.hold",
  ],
};
const run = { id: 10, status: "RUNNING", callerId: 2, supervisorId: 3 };
test("ALL administrator does not receive caller actions", () =>
  assert.deepEqual(actions("runs", run, me, { executions: [] }), []));
test("book editor cannot see independent approval", () =>
  assert.deepEqual(
    actions("books", { status: "SUBMITTED", createdBy: 2 }, me, {
      editors: [{ actorId: 1 }],
    }),
    [],
  ));
test("operator only sees own completion", () => {
  const e = { id: 4, status: "CALLED", operatorId: 2 };
  assert.deepEqual(actions("executions", e, me, { record: run }), []);
});
test("completion remains visible during hold", () =>
  assert.deepEqual(
    actions("executions", { id: 4, status: "CALLED", operatorId: 1 }, me, {
      record: { ...run, status: "HELD" },
    }),
    ["done"],
  ));
test("held run does not offer new cue call", () =>
  assert.deepEqual(
    actions(
      "executions",
      { id: 4, status: "READY", operatorId: 3 },
      { ...me, id: 2 },
      {
        record: { ...run, status: "HELD" },
        executions: [{ id: 4, status: "READY" }],
      },
    ),
    [],
  ));
test("caller cannot skip mandatory first cue or jump", () => {
  const first = { id: 4, status: "PENDING", optional: false },
    second = { id: 5, status: "PENDING", optional: true };
  assert.deepEqual(
    actions(
      "executions",
      first,
      { ...me, id: 2 },
      { record: run, executions: [first, second] },
    ),
    ["standby"],
  );
  assert.deepEqual(
    actions(
      "executions",
      second,
      { ...me, id: 2 },
      { record: run, executions: [first, second] },
    ),
    [],
  );
});
test("wall time uses Shanghai offset and undeclared states excluded", () => {
  const result = payload(
    { plannedAt: "2026-10-06T20:00", status: "CLOSED", callerId: "4" },
    [
      ["plannedAt", "", "", "datetime"],
      ["callerId", "", "", "id"],
    ],
  );
  assert.deepEqual(result, {
    plannedAt: "2026-10-06T12:00:00.000Z",
    callerId: 4,
  });
  assert.equal(localTime(result.plannedAt), "2026-10-06T20:00");
});
