// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 前端动作提示与有限输入；真实权限和状态始终由后端校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const states = {
  DRAFT: ["草稿", "Draft"],
  RETURNED: ["已退回", "Returned"],
  SUBMITTED: ["待审批", "Submitted"],
  APPROVED: ["已批准", "Approved"],
  CANCELLED: ["已取消", "Cancelled"],
  PREPARING: ["岗位准备", "Preparing"],
  RUNNING: ["进行中", "Running"],
  HELD: ["已暂停", "Held"],
  ENDED: ["待复盘", "Ended"],
  ABORTED: ["中止待复盘", "Aborted"],
  CLOSED: ["已复盘", "Closed"],
  PENDING: ["待执行", "Pending"],
  STANDBY: ["已通知待命", "Standby"],
  READY: ["操作员就绪", "Ready"],
  CALLED: ["已记录提示", "Called"],
  DONE: ["已回报完成", "Done"],
  SKIPPED: ["已略过", "Skipped"],
  OPEN: ["待处理", "Open"],
  RESOLVED: ["已核实", "Resolved"],
};
/** 按指派显示动作，ALL范围不会在界面上赋予提示员或操作员职责。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actions(type, row, me, detail = {}) {
  if (!row || !me) return [];
  const has = (p) => me.permissions?.includes(p);
  const result = [];
  if (type === "books") {
    if (has("book.write") && ["DRAFT", "RETURNED"].includes(row.status))
      result.push("submit");
    if (
      has("book.write") &&
      me.id === row.createdBy &&
      ["DRAFT", "RETURNED", "SUBMITTED"].includes(row.status)
    )
      result.push("cancel");
    if (
      has("book.write") &&
      me.id === row.createdBy &&
      row.status === "SUBMITTED"
    )
      result.push("withdraw");
    if (
      has("book.review") &&
      row.status === "SUBMITTED" &&
      !(detail.editors || []).some((e) => e.actorId === me.id)
    )
      result.push("approve", "reject");
  }
  if (type === "runs") {
    if (has("run.call") && me.id === row.callerId) {
      if (row.status === "PREPARING") result.push("start", "cancel");
      if (row.status === "RUNNING") result.push("end", "abort");
      if (row.status === "HELD") result.push("resume", "abort");
    }
    if (
      has("run.hold") &&
      row.status === "RUNNING" &&
      (me.id === row.callerId ||
        (detail.executions || []).some((e) => e.operatorId === me.id))
    )
      result.push("hold");
    if (
      has("run.review") &&
      me.id === row.supervisorId &&
      ["ENDED", "ABORTED"].includes(row.status)
    )
      result.push("close");
  }
  if (type === "executions") {
    const r = detail.record;
    if (!r) return [];
    if (has("run.write") && r.status === "PREPARING") result.push("assign");
    if (has("cue.operate") && me.id === row.operatorId) {
      if (r.status === "PREPARING" && !row.receivedAt) result.push("receive");
      if (r.status === "RUNNING" && row.status === "STANDBY")
        result.push("ready");
      if (["RUNNING", "HELD"].includes(r.status) && row.status === "CALLED")
        result.push("done");
    }
    if (has("run.call") && me.id === r.callerId && r.status === "RUNNING") {
      const next = (detail.executions || []).find(
        (e) => !["DONE", "SKIPPED"].includes(e.status),
      );
      if (next?.id === row.id) {
        if (row.status === "PENDING") result.push("standby");
        if (row.status === "READY") result.push("call");
        if (
          row.optional &&
          ["PENDING", "STANDBY", "READY"].includes(row.status)
        )
          result.push("skip");
      }
    }
  }
  if (
    type === "holds" &&
    row.status === "OPEN" &&
    has("run.review") &&
    me.id === detail.record?.supervisorId &&
    me.id !== row.raisedBy
  )
    result.push("resolve");
  return result;
}
/** 将上海墙上时间转换为带明确偏移的API时刻。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localTime(value) {
  if (!value) return "";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "";
  return new Date(d.getTime() + 8 * 3600000).toISOString().slice(0, 16);
}
/** 仅序列化表单声明字段，排除业务状态与未声明的事实字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function payload(form, fields) {
  const result = {};
  for (const [key, , , type] of fields) {
    const v = form[key];
    result[key] =
      type === "datetime"
        ? v
          ? new Date(v + ":00+08:00").toISOString()
          : null
        : ["integer", "id"].includes(type)
          ? v === "" || v == null
            ? null
            : Number(v)
          : type === "boolean"
            ? Boolean(v)
            : type === "permissions"
              ? v || []
              : (v ?? "");
  }
  return result;
}
