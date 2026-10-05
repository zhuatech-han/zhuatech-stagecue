<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import {
  Clapperboard,
  ListMusic,
  Layers,
  BarChart3,
  Users,
  ShieldCheck,
  Settings,
  LogOut,
  Plus,
  Search,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  X,
  Download,
  RefreshCw,
  ExternalLink,
  Clock3,
  AlertCircle,
  PauseCircle,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import { states, actions, payload, localTime } from "./domain.js";
import { fields, commandFields } from "./forms.js";
const lang = ref(localStorage.getItem("stagecue-language") || "zh"),
  me = ref(null),
  view = ref("runs"),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  loginForm = ref({ username: "", password: "" }),
  options = ref({}),
  directories = ref({}),
  rows = ref([]),
  total = ref(0),
  page = ref(0),
  search = ref(""),
  filter = ref(""),
  sort = ref("newest"),
  detail = ref(null),
  stats = ref({ states: {} }),
  modal = ref(null),
  form = ref({}),
  contact = ref(false),
  eventPage = ref(0);
const t = (zh, en) => (lang.value === "zh" ? zh : en);
function language() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("stagecue-language", lang.value);
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
}
const business = ["productions", "books", "runs"],
  adminTypes = [
    "users",
    "roles",
    "departments",
    "menus",
    "permissions",
    "dictionaries",
    "settings",
  ];
const labels = {
  runs: ["场次执行", "Show runs"],
  books: ["提示本版次", "Cue books"],
  productions: ["制作目录", "Productions"],
  dashboard: ["场次统计", "Statistics"],
  audit: ["操作审计", "Audit"],
  users: ["账号管理", "Accounts"],
  roles: ["角色与权限", "Roles"],
  departments: ["部门管理", "Departments"],
  menus: ["导航管理", "Navigation"],
  permissions: ["权限目录", "Permissions"],
  dictionaries: ["提示类型", "Cue types"],
  settings: ["系统参数", "Settings"],
};
const icons = {
  runs: Clapperboard,
  books: ListMusic,
  productions: Layers,
  dashboard: BarChart3,
  audit: Clock3,
  users: Users,
  roles: ShieldCheck,
};
const title = computed(() =>
    t(...(labels[view.value] || ["StageCue", "StageCue"])),
  ),
  record = computed(() => detail.value?.record),
  can = (p) => me.value?.permissions?.includes(p),
  statusName = (s) => t(...(states[s] || [s, s]));
const actionNames = {
  SAVE: ["保存记录", "Save"],
  CUE_SAVE: ["保存提示", "Save cue"],
  RESET_AFTER_HOLD: ["恢复后重置待命", "Reset after hold"],
  RAISE: ["提出暂停", "Raise hold"],
  RESOLVE: ["核实暂停处理", "Resolve hold"],
  submit: ["提交审批", "Submit"],
  approve: ["独立批准", "Approve"],
  reject: ["退回修订", "Return"],
  withdraw: ["撤回修订", "Withdraw"],
  cancel: ["取消记录", "Cancel"],
  start: ["记录场次开始", "Start run"],
  hold: ["提出暂停", "Hold run"],
  resume: ["记录恢复", "Resume run"],
  end: ["结束并提交复盘", "End for review"],
  abort: ["记录中止", "Abort run"],
  close: ["完成独立复盘", "Close review"],
  assign: ["指派操作员", "Assign operator"],
  receive: ["确认收悉此版", "Receive revision"],
  standby: ["记录待命通知", "Record standby"],
  ready: ["确认就绪", "Confirm ready"],
  call: ["记录提示发出", "Record call"],
  done: ["回报执行完成", "Report completion"],
  skip: ["略过可选提示", "Skip optional cue"],
  resolve: ["核实处理完成", "Resolve hold"],
};
const actionName = (a) => t(...(actionNames[a] || [a, a]));
const failures = {
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Session expired. Sign in again"],
  FORBIDDEN: ["当前岗位无此权限", "Permission required"],
  OUT_OF_SCOPE: ["超出当前账号的数据范围", "Outside your data scope"],
  STALE_VERSION: [
    "记录已变化，请刷新后重试",
    "Record changed. Refresh before retrying",
  ],
  INVALID_STATE: ["当前状态不允许此操作", "Unavailable in this state"],
  INDEPENDENT_REVIEW_REQUIRED: [
    "须由未参与本版编辑的独立人员审批或复核",
    "Independent actor required",
  ],
  ASSIGNED_CALLER_REQUIRED: [
    "须由本场指定提示员操作",
    "Assigned caller required",
  ],
  ASSIGNED_OPERATOR_REQUIRED: [
    "须由本条指定操作员操作",
    "Assigned operator required",
  ],
  ASSIGNED_SUPERVISOR_REQUIRED: [
    "须由本场指定独立监督员操作",
    "Assigned supervisor required",
  ],
  ASSIGNED_MEMBER_REQUIRED: [
    "须为本场提示员或已指派操作员",
    "Assigned run member required",
  ],
  INELIGIBLE_ASSIGNMENT: [
    "指定账号已停用或没有所需岗位权限",
    "Account disabled or missing role permission",
  ],
  CREW_NOT_READY: [
    "所有提示须完成指派与收悉",
    "Assign and receive every cue first",
  ],
  CUE_OUT_OF_ORDER: ["须先处理前序提示", "Resolve earlier cues first"],
  MANDATORY_CUE_CANNOT_SKIP: [
    "必执行提示不能略过",
    "Mandatory cue cannot be skipped",
  ],
  MANDATORY_CUE_REQUIRED: [
    "提示本至少需要一条必执行提示",
    "Include a mandatory cue",
  ],
  OPEN_HOLD: ["暂停处理尚未完成独立核实", "Resolve the open hold first"],
  UNFINISHED_CUES: ["仍有未完成提示", "Complete all cues first"],
  EXECUTION_STILL_OPEN: [
    "已提示的执行仍未回报",
    "Report called cues before aborting",
  ],
  OPEN_REVISION: [
    "已有未完成版次，请先处理",
    "Resolve existing draft revision",
  ],
  SUPERSEDED_BOOK: [
    "此提示本已有新版获批，请选新版",
    "Choose the latest approved revision",
  ],
  IMMUTABLE_IDENTITY: ["编号或关联对象不可变更", "Identity cannot be changed"],
  DISABLED_RESOURCE: ["制作已停用", "Production disabled"],
  LOGIN_FAILED: ["账号或密码不正确", "Incorrect username or password"],
  LOGIN_THROTTLED: ["登录尝试过多，稍后重试", "Too many attempts. Try later"],
  WEAK_PASSWORD: [
    "密码至少12位，含大小写字母和数字",
    "Use 12+ characters, upper/lowercase and digits",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确", "Incorrect current password"],
  LAST_ADMIN: ["须保留一位启用管理员", "Keep an enabled administrator"],
  CONFLICT: [
    "编号重复或记录正在被引用",
    "Duplicate reference or referenced record",
  ],
  INVALID_INPUT: ["请检查必填项与输入格式", "Check fields and formats"],
  REQUEST_KEY_REUSED: [
    "此次请求已用于不同操作，请重新打开表单",
    "Request conflicts. Reopen form",
  ],
  RECORD_LIMIT: ["已达到此类记录上限", "Record limit reached"],
  CUE_LIMIT: ["每版最多200条提示", "Up to 200 cues per revision"],
};
function clearSession() {
  me.value = null;
  detail.value = null;
  rows.value = [];
  modal.value = null;
  options.value = {};
  directories.value = {};
  resetCsrf();
}
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    return await fn();
  } catch (e) {
    error.value = failures[e.message]
      ? t(...failures[e.message])
      : t("操作失败：", "Action failed: ") + e.message;
    if (e.message === "UNAUTHENTICATED") clearSession();
  } finally {
    busy.value = false;
  }
}
async function loadOptions() {
  options.value = await api("/options");
  if (can("admin") && me.value.scope === "ALL")
    for (const k of ["roles", "permissions", "departments"])
      directories.value[k] = await api("/admin/" + k);
}
async function load() {
  detail.value = null;
  if (view.value === "dashboard") {
    stats.value = await api("/dashboard");
    return;
  }
  if (business.includes(view.value)) {
    const r = await api(
      "/" +
        view.value +
        "?" +
        new URLSearchParams({
          search: search.value,
          status: filter.value,
          page: String(page.value),
          size: "12",
          sort: sort.value,
        }),
    );
    rows.value = r.content;
    total.value = r.total;
  } else {
    let all = await api(
      view.value === "audit" ? "/audit" : "/admin/" + view.value,
    );
    all = all.filter((v) =>
      Object.values(v).some(
        (x) =>
          typeof x === "string" &&
          x.toLowerCase().includes(search.value.toLowerCase()),
      ),
    );
    all.sort((a, b) => (sort.value === "oldest" ? a.id - b.id : b.id - a.id));
    total.value = all.length;
    rows.value = all.slice(page.value * 12, page.value * 12 + 12);
  }
}
async function navigate(code) {
  if (busy.value) return;
  view.value = code;
  page.value = 0;
  search.value = "";
  filter.value = "";
  rows.value = [];
  detail.value = null;
  await run(load);
  window.scrollTo(0, 0);
}
async function signIn() {
  await run(async () => {
    resetCsrf();
    me.value = await api("/auth/login", "POST", loginForm.value);
    loginForm.value.password = "";
    await loadOptions();
    view.value = me.value.menus[0]?.code || "dashboard";
    await load();
  });
}
async function signOut() {
  await run(async () => {
    await api("/auth/logout", "POST", {});
    clearSession();
  });
}
async function open(row) {
  await run(async () => {
    detail.value = await api("/" + view.value + "/" + row.id);
    eventPage.value = 0;
    window.scrollTo(0, 0);
  });
}
async function refresh() {
  await run(async () => {
    me.value = await api("/auth/me");
    await loadOptions();
    if (record.value)
      detail.value = await api("/" + view.value + "/" + record.value.id);
    else await load();
  });
}
const currentActions = computed(() =>
    actions(view.value, record.value, me.value, detail.value),
  ),
  events = computed(() => [...(detail.value?.events || [])].reverse()),
  eventRows = computed(() =>
    events.value.slice(eventPage.value * 10, eventPage.value * 10 + 10),
  );
const modalFields = computed(() =>
  modal.value?.kind === "command"
    ? commandFields(modal.value.action)
    : modal.value?.kind === "password"
      ? [
          ["oldPassword", "原密码", "Current password", "password"],
          ["newPassword", "新密码", "New password", "password"],
        ]
      : fields[modal.value?.type] || [],
);
function choices(key) {
  if (key === "scope")
    return ["ALL", "DEPARTMENT", "SELF"].map((value) => ({
      value,
      label: {
        ALL: t("全部", "All"),
        DEPARTMENT: t("本部门", "Department"),
        SELF: t("本人创建或获指派", "Created by self or assigned"),
      }[value],
    }));
  let list = directories.value[key] || options.value[key] || [];
  if (["callers", "supervisors", "operators"].includes(key)) {
    const p = {
      callers: "run.call",
      supervisors: "run.review",
      operators: "cue.operate",
    }[key];
    list = (options.value.people || []).filter((a) =>
      a.permissions.includes(p),
    );
    if (key === "operators" && record.value)
      list = list.filter(
        (a) =>
          a.id !== record.value.callerId && a.id !== record.value.supervisorId,
      );
  }
  if (key === "approvedBooks")
    list = (options.value.books || []).filter((b) => b.status === "APPROVED");
  return list.map((v) => ({
    value: ["permissions", "kinds", "modes"].includes(key) ? v.code : v.id,
    label:
      lang.value === "en" && v.nameEn
        ? v.nameEn
        : [
            v.reference,
            v.name || v.displayName || v.title || v.code,
            v.revision ? "v" + v.revision : "",
          ]
            .filter(Boolean)
            .join(" · "),
  }));
}
function edit(type, row = null) {
  error.value = "";
  modal.value = {
    kind: "edit",
    type,
    id: row?.id,
    returnId: record.value?.id,
    returnType: view.value,
  };
  form.value = row
    ? { ...row }
    : {
        enabled: true,
        departmentId: me.value.departmentId,
        scope: "DEPARTMENT",
        permissions: [],
        type: "cue",
        kind: "LIGHT",
        optional: false,
        sequence:
          (detail.value?.cues || []).reduce(
            (n, c) => Math.max(n, c.sequence),
            0,
          ) + 1,
        bookId: type === "cues" ? record.value?.id : "",
        productionId:
          type === "books" && view.value === "productions"
            ? record.value?.id
            : "",
        plannedAt: localTime(new Date()),
        mode: "REHEARSAL",
      };
  for (const f of fields[type] || [])
    if (f[3] === "datetime" && row) form.value[f[0]] = localTime(row[f[0]]);
  if (type === "users") form.value.password = "";
}
function command(type, row, action) {
  error.value = "";
  modal.value = {
    kind: "command",
    type,
    id: row.id,
    version: row.version,
    action,
    returnId: record.value.id,
    returnType: view.value,
  };
  form.value = { note: "", operatorId: row.operatorId || "" };
}
function immutable(key) {
  return (
    modal.value?.kind === "edit" &&
    modal.value.id &&
    (["reference", "departmentId", "productionId", "bookId"].includes(key) ||
      (modal.value.type === "dictionaries" && ["type", "code"].includes(key)))
  );
}
/** 重试保留请求键，字段或版本改变才生成新键。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function save() {
  await run(async () => {
    const m = modal.value;
    const body = payload(form.value, modalFields.value);
    if (m.kind === "password") {
      await api("/auth/password", "POST", body);
      clearSession();
      return;
    }
    if (m.kind === "delete") {
      await api("/admin/" + m.type + "/" + m.id, "DELETE", {});
    } else {
      if (!adminTypes.includes(m.type)) {
        body.version =
          m.kind === "command" ? m.version : (form.value.version ?? null);
        const signature = JSON.stringify(body);
        if (signature !== m.signature) {
          m.requestKey = crypto.randomUUID();
          m.signature = signature;
        }
        body.requestKey = m.requestKey;
      }
      await api(
        "/" +
          (adminTypes.includes(m.type) ? "admin/" : "") +
          m.type +
          (m.id ? "/" + m.id : "") +
          (m.kind === "command" ? "/commands/" + m.action : ""),
        m.kind === "edit" && m.id ? "PUT" : "POST",
        body,
      );
    }
    modal.value = null;
    if (adminTypes.includes(m.type)) me.value = await api("/auth/me");
    await loadOptions();
    if (m.returnId && business.includes(m.returnType))
      detail.value = await api("/" + m.returnType + "/" + m.returnId);
    else await load();
    notice.value = t("已保存", "Saved");
  });
}
function formatTime(v) {
  return v
    ? new Intl.DateTimeFormat(lang.value === "zh" ? "zh-CN" : "en-GB", {
        timeZone: "Asia/Shanghai",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
      }).format(new Date(v))
    : "—";
}
function value(row, key) {
  if (key.endsWith("At")) return formatTime(row[key]);
  if (key === "status") return statusName(row[key]);
  if (key === "permissions")
    return (row.permissions?.length || 0) + t(" 项权限", " permissions");
  if (key === "enabled" || key === "optional")
    return row[key]
      ? t(
          key === "optional" ? "可略过" : "启用",
          key === "optional" ? "Optional" : "Enabled",
        )
      : t(
          key === "optional" ? "必执行" : "停用",
          key === "optional" ? "Mandatory" : "Disabled",
        );
  if (key === "scope")
    return (
      choices("scope").find((c) => c.value === row[key])?.label || row[key]
    );
  if (key === "kind")
    return (
      choices("kinds").find((c) => c.value === row[key])?.label || row[key]
    );
  const dir = {
    roleId: directories.value.roles,
    departmentId: directories.value.departments || options.value.departments,
    productionId: options.value.productions,
    bookId: options.value.books,
    operatorId: options.value.people,
    callerId: options.value.people,
    supervisorId: options.value.people,
  }[key];
  const entry = dir?.find((d) => d.id === row[key]);
  return entry?.name || entry?.title || row[key] || "—";
}
const columns = computed(
  () =>
    ({
      runs: [
        ["reference", "场次编号", "Reference"],
        ["productionName", "制作", "Production"],
        ["location", "场地", "Location"],
        ["bookRevision", "版次", "Revision"],
        ["plannedAt", "计划时间", "Planned"],
        ["status", "状态", "Status"],
      ],
      books: [
        ["title", "提示本", "Cue book"],
        ["productionId", "制作", "Production"],
        ["revision", "版次", "Revision"],
        ["status", "状态", "Status"],
      ],
      settings: [
        ["code", "参数", "Setting"],
        ["value", "参数值", "Value"],
      ],
      audit: [
        ["createdAt", "时间", "Time"],
        ["actor", "操作人", "Actor"],
        ["action", "操作", "Action"],
        ["objectId", "记录", "Record"],
      ],
    })[view.value] ||
    (fields[view.value] || []).filter((f) => f[0] !== "password").slice(0, 5),
);
const canCreate = computed(() =>
  business.includes(view.value)
    ? can(
        {
          runs: "run.write",
          books: "book.write",
          productions: "production.write",
        }[view.value],
      )
    : can("admin") &&
      ["users", "roles", "departments", "dictionaries"].includes(view.value),
);
const editable = (r) =>
  adminTypes.includes(view.value) ||
  (view.value === "productions" && can("production.write")) ||
  (view.value === "books" &&
    can("book.write") &&
    ["DRAFT", "RETURNED"].includes(r.status)) ||
  (view.value === "runs" && can("run.write") && r.status === "PREPARING");
const canDelete = computed(
  () =>
    can("admin") &&
    ["users", "roles", "departments", "dictionaries"].includes(view.value),
);
const filterStates = computed(() =>
  Object.keys(states).filter((s) =>
    (view.value === "books"
      ? ["DRAFT", "RETURNED", "SUBMITTED", "APPROVED", "CANCELLED"]
      : [
          "PREPARING",
          "RUNNING",
          "HELD",
          "ENDED",
          "ABORTED",
          "CLOSED",
          "CANCELLED",
        ]
    ).includes(s),
  ),
);
async function downloadReport() {
  await run(async () => {
    const data = await api(
      "/" + view.value + "/" + record.value.id + "/report.json",
    );
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = view.value + "-" + record.value.id + ".json";
    a.click();
    URL.revokeObjectURL(url);
  });
}
let poll;
onMounted(async () => {
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
  try {
    me.value = await api("/auth/me");
    await loadOptions();
    view.value = me.value.menus[0]?.code || "dashboard";
    await run(load);
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") error.value = e.message;
  }
  poll = setInterval(async () => {
    if (!me.value || busy.value || modal.value) return;
    try {
      me.value = await api("/auth/me");
      if (!me.value.menus.some((m) => m.code === view.value))
        await navigate(me.value.menus[0]?.code || "dashboard");
    } catch (e) {
      if (e.message === "UNAUTHENTICATED") clearSession();
    }
  }, 20000);
});
onUnmounted(() => clearInterval(poll));
</script>
<template>
  <div v-if="!me" class="login-layout">
    <section class="stage-art" aria-hidden="true">
      <div class="art-word">STAGE / CUE</div>
      <div class="stage-curtain"></div>
      <div class="stage-lines">
        <span>STANDBY</span><b>01</b><span>READY</span><b>02</b><span>CALL</span
        ><b>03</b>
      </div>
      <div class="art-caption">
        {{ t("版次 · 岗位 · 场次", "REVISION · CREW · RUN") }}
      </div>
    </section>
    <main class="login-panel">
      <div class="login-top">
        <img src="/brand/logo.jpg" alt="知华科技 LOGO" /><button
          class="plain"
          @click="language"
        >
          {{ lang === "zh" ? "EN" : "中文" }}
        </button>
      </div>
      <div class="login-heading">
        <span class="eyebrow">STAGECUE</span>
        <h1>{{ t("舞台演出提示与执行台账", "Cue books & show runs") }}</h1>
        <p>{{ t("登录你的业务工作台", "Sign in to your workspace") }}</p>
      </div>
      <form @submit.prevent="signIn">
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="loginForm.username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="loginForm.password"
            type="password"
            autocomplete="current-password"
            required
        /></label>
        <p v-if="error" class="error" role="alert">
          <AlertCircle :size="17" />{{ error }}
        </p>
        <button class="primary login-submit" :disabled="busy">
          {{ busy ? t("正在登录", "Signing in") : t("登录", "Sign in")
          }}<ArrowRight :size="18" />
        </button>
      </form>
      <div class="login-footer">
        <button class="plain" @click="contact = true">
          {{ t("知华科技 · 商业咨询", "ZhuaTech · Commercial enquiries") }}
        </button>
        <p>
          {{
            t("公开源码学习版／非商业源码版", "Non-commercial source edition")
          }}
        </p>
      </div>
    </main>
  </div>
  <div v-else class="workspace">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技 LOGO" />
        <div>
          <strong>StageCue</strong
          ><span>{{ t("舞台提示与执行", "Cue books & runs") }}</span>
        </div>
      </div>
      <nav :aria-label="t('主要导航', 'Main navigation')">
        <template v-for="(m, i) in me.menus" :key="m.code"
          ><p
            v-if="i === 0 || m.code === 'dashboard' || m.code === 'users'"
            class="nav-section"
          >
            {{
              i === 0
                ? t("场次协同", "OPERATIONS")
                : m.code === "dashboard"
                  ? t("统计与审计", "OVERVIEW")
                  : t("系统管理", "ADMINISTRATION")
            }}
          </p>
          <button
            :class="{ active: view === m.code }"
            :disabled="busy"
            @click="navigate(m.code)"
          >
            <component :is="icons[m.code] || Settings" :size="18" /><span>{{
              lang === "en" ? m.nameEn : m.name
            }}</span>
          </button></template
        >
      </nav>
      <div class="sidebar-footer">
        <button class="plain" @click="contact = true">
          <ExternalLink :size="14" />{{
            t("知华科技 · 咨询", "ZhuaTech · Enquiries")
          }}</button
        ><span>{{ t("非商业源码版 0.1.0", "Non-commercial 0.1.0") }}</span>
      </div>
    </aside>
    <div class="work-area">
      <header class="topbar">
        <span>{{ options.companyName || "StageCue" }}</span>
        <div class="top-actions">
          <button class="plain" @click="language">
            {{ lang === "zh" ? "EN" : "中文" }}</button
          ><button
            class="account-button"
            @click="
              modal = { kind: 'password' };
              form = {};
            "
          >
            <span class="avatar">{{ me.displayName?.slice(0, 1) }}</span
            ><span
              >{{ me.displayName }}<small>{{ me.role }}</small></span
            ></button
          ><button
            class="icon-button"
            :aria-label="t('退出', 'Sign out')"
            :disabled="busy"
            @click="signOut"
          >
            <LogOut :size="17" />
          </button>
        </div>
      </header>
      <main class="main-content">
        <div class="page-heading">
          <div>
            <span class="eyebrow">STAGECUE / {{ view.toUpperCase() }}</span>
            <h1>{{ title }}</h1>
          </div>
          <div class="row-actions">
            <button
              class="icon-button"
              :aria-label="t('刷新', 'Refresh')"
              :disabled="busy"
              @click="refresh"
            >
              <RefreshCw :size="17" /></button
            ><button
              v-if="detail"
              class="secondary"
              :disabled="busy"
              @click="run(load)"
            >
              <ChevronLeft :size="16" />{{
                t("返回列表", "Back to list")
              }}</button
            ><button
              v-else-if="canCreate"
              class="primary"
              :disabled="busy"
              @click="edit(view)"
            >
              <Plus :size="17" />{{ t("新建", "New") }}
            </button>
          </div>
        </div>
        <p v-if="error" class="error" role="alert">
          <AlertCircle :size="17" />{{ error }}
        </p>
        <p v-if="notice" class="notice" role="status">{{ notice }}</p>
        <template v-if="view === 'dashboard'"
          ><section class="metric-grid">
            <article
              v-for="m in [
                ['runs', '场次', 'Runs', Clapperboard],
                ['cueTotal', '提示条数', 'Cues', ListMusic],
                ['cueDone', '已完成提示', 'Completed cues', ShieldCheck],
                ['held', '暂停场次', 'Held runs', PauseCircle],
              ]"
              :key="m[0]"
              class="metric"
            >
              <component :is="m[3]" :size="22" /><span>{{ t(m[1], m[2]) }}</span
              ><strong>{{ stats[m[0]] || 0 }}</strong>
            </article>
          </section>
          <section class="panel">
            <h2>{{ t("场次状态", "Run statuses") }}</h2>
            <div
              v-for="(count, status) in stats.states"
              :key="status"
              class="chart-row"
            >
              <span>{{ statusName(status) }}</span>
              <div>
                <i
                  :style="{
                    width: (count / Math.max(stats.runs, 1)) * 100 + '%',
                  }"
                ></i>
              </div>
              <strong>{{ count }}</strong>
            </div>
            <p v-if="!stats.runs" class="empty">
              {{ t("暂无可见场次", "No visible runs") }}
            </p>
          </section></template
        >
        <section v-else-if="detail" class="detail-layout">
          <div class="detail-main">
            <article class="panel">
              <div class="record-heading">
                <div>
                  <span class="eyebrow">{{
                    record.reference || "V" + record.revision
                  }}</span>
                  <h2>
                    {{ record.title || record.name || record.productionName }}
                  </h2>
                </div>
                <span
                  v-if="record.status"
                  class="status"
                  :data-status="record.status"
                  >{{ statusName(record.status) }}</span
                >
              </div>
              <div v-if="view === 'runs'" class="record-meta">
                <span>{{ record.location }}</span
                ><span
                  >{{ t("提示本版次", "Book revision") }}
                  {{ record.bookRevision }}</span
                ><span>{{
                  record.mode === "REHEARSAL"
                    ? t("排练", "Rehearsal")
                    : t("正式场次", "Performance")
                }}</span
                ><span
                  >{{ t("提示员", "Caller") }}
                  {{ value(record, "callerId") }}</span
                ><span
                  >{{ t("监督员", "Supervisor") }}
                  {{ value(record, "supervisorId") }}</span
                >
              </div>
              <div v-if="view === 'runs'" class="time-grid">
                <article>
                  <span>{{ t("计划时间", "Planned") }}</span
                  ><strong>{{ formatTime(record.plannedAt) }}</strong>
                </article>
                <article class="actual">
                  <span>{{ t("开始记录", "Start recorded") }}</span
                  ><strong>{{ formatTime(record.startedAt) }}</strong>
                </article>
                <article class="actual">
                  <span>{{ t("结束记录", "End recorded") }}</span
                  ><strong>{{ formatTime(record.endedAt) }}</strong>
                </article>
                <article class="actual">
                  <span>{{ t("独立复盘", "Reviewed") }}</span
                  ><strong>{{ formatTime(record.closedAt) }}</strong>
                </article>
              </div>
              <div v-if="view === 'books'" class="record-meta">
                <span
                  >{{ t("制作", "Production") }}
                  {{ value(record, "productionId") }}</span
                ><span>{{ t("版次", "Revision") }} {{ record.revision }}</span
                ><span
                  >{{ t("批准时间", "Approved") }}
                  {{ formatTime(record.approvedAt) }}</span
                >
              </div>
              <div class="record-footer">
                <span>{{
                  t(
                    "上海时区 · 操作记录",
                    "Shanghai time · Operational records",
                  )
                }}</span
                ><button
                  v-if="can('export')"
                  class="plain"
                  :disabled="busy"
                  @click="downloadReport"
                >
                  <Download :size="15" />{{
                    t("下载业务证据", "Download evidence")
                  }}
                </button>
              </div>
            </article>
            <article v-if="view === 'books'" class="panel">
              <div class="panel-heading">
                <h2>{{ t("本版提示", "Revision cues") }}</h2>
                <button
                  v-if="
                    can('book.write') &&
                    ['DRAFT', 'RETURNED'].includes(record.status)
                  "
                  class="secondary"
                  @click="edit('cues')"
                >
                  <Plus :size="16" />{{ t("添加提示", "Add cue") }}
                </button>
              </div>
              <div
                v-for="c in detail.cues"
                :key="c.id"
                class="cue-card"
                :class="{ excluded: !c.enabled }"
              >
                <div class="cue-top">
                  <span class="cue-number">{{ c.sequence }}</span
                  ><strong>{{ c.code }}</strong
                  ><span class="priority">{{ value(c, "kind") }}</span
                  ><span class="muted">{{ value(c, "optional") }}</span
                  ><button
                    v-if="
                      can('book.write') &&
                      ['DRAFT', 'RETURNED'].includes(record.status)
                    "
                    class="plain"
                    @click="edit('cues', c)"
                  >
                    {{ t("编辑", "Edit") }}
                  </button>
                </div>
                <p class="trigger">{{ c.triggerText }}</p>
                <p>{{ c.description }}</p>
                <span v-if="!c.enabled" class="muted">{{
                  t("未列入此版", "Excluded from revision")
                }}</span>
              </div>
              <p v-if="!detail.cues.length" class="empty">
                {{ t("尚未添加提示", "No cues yet") }}
              </p>
            </article>
            <article v-if="view === 'runs'" class="panel">
              <div class="panel-heading">
                <h2>{{ t("提示执行", "Cue execution") }}</h2>
                <span
                  >{{ detail.executions.length }} {{ t("条", "cues") }}</span
                >
              </div>
              <div v-for="e in detail.executions" :key="e.id" class="cue-card">
                <div class="cue-top">
                  <span class="cue-number">{{ e.sequence }}</span
                  ><strong>{{ e.code }}</strong
                  ><span class="priority">{{ value(e, "kind") }}</span
                  ><span class="status" :data-status="e.status">{{
                    statusName(e.status)
                  }}</span>
                </div>
                <p class="trigger">{{ e.triggerText }}</p>
                <p>{{ e.description }}</p>
                <div class="cue-metadata">
                  <span>{{ value(e, "optional") }}</span
                  ><span
                    >{{ t("操作员", "Operator") }}
                    {{ value(e, "operatorId") }}</span
                  ><span>{{
                    e.receivedAt
                      ? t("已收悉", "Received")
                      : t("尚未收悉", "Not received")
                  }}</span
                  ><span v-if="e.calledAt"
                    >{{ t("提示记录", "Call recorded") }}
                    {{ formatTime(e.calledAt) }}</span
                  ><span v-if="e.doneAt"
                    >{{ t("完成回报", "Completion recorded") }}
                    {{ formatTime(e.doneAt) }}</span
                  >
                </div>
                <div
                  v-if="actions('executions', e, me, detail).length"
                  class="cue-actions"
                >
                  <button
                    v-for="a in actions('executions', e, me, detail)"
                    :key="a"
                    class="secondary"
                    :disabled="busy"
                    @click="command('executions', e, a)"
                  >
                    {{ actionName(a) }}<ArrowRight :size="14" />
                  </button>
                </div>
              </div>
            </article>
            <article
              v-if="view === 'runs' && detail.holds.length"
              class="panel"
            >
              <h2>{{ t("暂停与处理", "Holds & resolution") }}</h2>
              <div v-for="h in detail.holds" :key="h.id" class="hold-card">
                <span class="status" :data-status="h.status">{{
                  statusName(h.status)
                }}</span>
                <p>{{ h.reason }}</p>
                <small>{{ formatTime(h.raisedAt) }}</small>
                <p v-if="h.resolution">{{ h.resolution }}</p>
                <button
                  v-for="a in actions('holds', h, me, detail)"
                  :key="a"
                  class="secondary"
                  :disabled="busy"
                  @click="command('holds', h, a)"
                >
                  {{ actionName(a) }}
                </button>
              </div>
            </article>
            <article class="panel">
              <div class="panel-heading">
                <h2>{{ t("核实与操作记录", "Evidence & activity") }}</h2>
                <span>{{ events.length }} {{ t("条", "records") }}</span>
              </div>
              <ol class="timeline">
                <li v-for="e in eventRows" :key="e.id">
                  <i></i>
                  <div>
                    <header>
                      <strong>{{ actionName(e.action) }}</strong
                      ><time>{{ formatTime(e.createdAt) }}</time>
                    </header>
                    <p>{{ e.note || t("记录已保存", "Record saved") }}</p>
                    <small>{{ t("操作账号", "Actor") }} #{{ e.actorId }}</small>
                  </div>
                </li>
              </ol>
              <div v-if="events.length > 10" class="pagination">
                <button
                  class="icon-button"
                  :disabled="eventPage === 0"
                  @click="eventPage--"
                >
                  <ChevronLeft :size="17" /></button
                ><span
                  >{{ eventPage + 1 }} /
                  {{ Math.ceil(events.length / 10) }}</span
                ><button
                  class="icon-button"
                  :disabled="(eventPage + 1) * 10 >= events.length"
                  @click="eventPage++"
                >
                  <ChevronRight :size="17" />
                </button>
              </div>
            </article>
          </div>
          <aside class="action-panel panel">
            <h2>{{ t("当前操作", "Available actions") }}</h2>
            <span
              v-if="record.status"
              class="status"
              :data-status="record.status"
              >{{ statusName(record.status) }}</span
            ><button
              v-if="editable(record)"
              class="secondary"
              :disabled="busy"
              @click="edit(view, record)"
            >
              {{ t("编辑记录", "Edit record") }}</button
            ><button
              v-for="a in currentActions"
              :key="a"
              class="primary"
              :disabled="busy"
              @click="command(view, record, a)"
            >
              {{ actionName(a) }}<ArrowRight :size="16" /></button
            ><button
              v-if="
                view === 'productions' && can('book.write') && record.enabled
              "
              class="secondary"
              @click="edit('books')"
            >
              {{ t("建立提示本版次", "Create book revision") }}
            </button>
            <p v-if="!currentActions.length && !editable(record)" class="muted">
              {{
                t(
                  "当前岗位暂无可执行动作",
                  "No available actions for your role",
                )
              }}
            </p>
            <div class="action-note">
              <span>{{ t("记录版本", "Record version") }}</span
              ><strong>{{ record.version }}</strong>
            </div>
          </aside>
        </section>
        <section v-else class="panel list-panel">
          <form
            class="filters"
            @submit.prevent="
              page = 0;
              run(load);
            "
          >
            <label class="search-box"
              ><Search :size="17" /><input
                v-model="search"
                :placeholder="t('搜索编号、名称', 'Search reference or name')"
                :aria-label="t('搜索', 'Search')"
                maxlength="120" /></label
            ><select
              v-if="['books', 'runs'].includes(view)"
              v-model="filter"
              :aria-label="t('状态筛选', 'Filter status')"
              @change="
                page = 0;
                run(load);
              "
            >
              <option value="">{{ t("全部状态", "All statuses") }}</option>
              <option v-for="s in filterStates" :key="s" :value="s">
                {{ statusName(s) }}
              </option></select
            ><select
              v-model="sort"
              :aria-label="t('排序', 'Sort')"
              @change="
                page = 0;
                run(load);
              "
            >
              <option value="newest">{{ t("最近新增", "Newest") }}</option>
              <option value="oldest">
                {{ t("最早新增", "Oldest") }}
              </option></select
            ><button class="secondary" :disabled="busy">
              {{ t("查询", "Search") }}
            </button>
          </form>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th v-for="c in columns" :key="c[0]">{{ t(c[1], c[2]) }}</th>
                  <th>{{ t("操作", "Actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rows" :key="r.id">
                  <td v-for="c in columns" :key="c[0]">
                    <span
                      v-if="c[0] === 'status'"
                      class="status"
                      :data-status="r.status"
                      >{{ statusName(r.status) }}</span
                    ><span v-else>{{ value(r, c[0]) }}</span>
                  </td>
                  <td>
                    <div class="row-actions">
                      <button
                        v-if="business.includes(view)"
                        class="row-button"
                        :disabled="busy"
                        @click="open(r)"
                      >
                        {{ t("详情", "Details")
                        }}<ChevronRight :size="14" /></button
                      ><button
                        v-else-if="can('admin') && editable(r)"
                        class="row-button"
                        @click="edit(view, r)"
                      >
                        {{ t("编辑", "Edit") }}</button
                      ><button
                        v-if="canDelete"
                        class="row-button danger"
                        @click="
                          modal = { kind: 'delete', type: view, id: r.id };
                          form = {};
                        "
                      >
                        {{ t("删除", "Delete") }}
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-if="!rows.length" class="empty">
            {{ t("暂无记录", "No records") }}
          </p>
          <div class="pagination">
            <span>{{ total }} {{ t("条记录", "records") }}</span>
            <div>
              <button
                class="icon-button"
                :disabled="page === 0 || busy"
                @click="
                  page--;
                  run(load);
                "
              >
                <ChevronLeft :size="17" /></button
              ><span
                >{{ page + 1 }} / {{ Math.max(1, Math.ceil(total / 12)) }}</span
              ><button
                class="icon-button"
                :disabled="(page + 1) * 12 >= total || busy"
                @click="
                  page++;
                  run(load);
                "
              >
                <ChevronRight :size="17" />
              </button>
            </div>
          </div>
        </section>
      </main>
    </div>
  </div>
  <div
    v-if="modal"
    class="modal-backdrop"
    @click.self="!busy && (modal = null)"
  >
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="
        modal.kind === 'command'
          ? actionName(modal.action)
          : t('编辑记录', 'Edit record')
      "
    >
      <header>
        <h2>
          {{
            modal.kind === "command"
              ? actionName(modal.action)
              : modal.kind === "password"
                ? t("修改密码", "Change password")
                : modal.kind === "delete"
                  ? t("删除记录", "Delete record")
                  : t("编辑记录", "Edit record")
          }}
        </h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          :disabled="busy"
          @click="modal = null"
        >
          <X :size="17" />
        </button>
      </header>
      <form @submit.prevent="save">
        <p v-if="modal.kind === 'delete'" class="delete-note">
          {{
            t(
              "删除未引用的管理记录？业务历史会阻止删除已引用记录。",
              "Delete this unreferenced directory record? Referenced records are protected.",
            )
          }}
        </p>
        <div v-else class="form-grid">
          <template v-for="f in modalFields" :key="f[0]"
            ><fieldset
              v-if="f[3] === 'permissions'"
              class="permissions-field full-width"
            >
              <legend>{{ t(f[1], f[2]) }}</legend>
              <label
                v-for="p in directories.permissions || []"
                :key="p.code"
                class="check"
                ><input
                  v-model="form.permissions"
                  type="checkbox"
                  :value="p.code"
                />{{ p.name }}<small>{{ p.code }}</small></label
              >
            </fieldset>
            <label v-else-if="f[3] === 'boolean'" class="check"
              ><input v-model="form[f[0]]" type="checkbox" />{{
                t(f[1], f[2])
              }}</label
            ><label v-else :class="{ 'full-width': f[3] === 'textarea' }"
              >{{ t(f[1], f[2])
              }}<select
                v-if="['id', 'select'].includes(f[3])"
                v-model="form[f[0]]"
                :disabled="
                  immutable(f[0]) ||
                  (modal.type === 'cues' && f[0] === 'bookId')
                "
                required
              >
                <option value="">{{ t("请选择", "Choose") }}</option>
                <option
                  v-for="c in choices(f[4])"
                  :key="c.value"
                  :value="c.value"
                >
                  {{ c.label }}
                </option></select
              ><textarea
                v-else-if="f[3] === 'textarea'"
                v-model="form[f[0]]"
                rows="3"
                required
                :maxlength="f[0] === 'triggerText' ? 300 : 1000"
              ></textarea
              ><input
                v-else
                v-model="form[f[0]]"
                :type="
                  f[3] === 'password'
                    ? 'password'
                    : f[3] === 'datetime'
                      ? 'datetime-local'
                      : f[3] === 'integer'
                        ? 'number'
                        : 'text'
                "
                :readonly="immutable(f[0])"
                :required="
                  !(modal.type === 'users' && modal.id && f[0] === 'password')
                "
                :maxlength="
                  f[0] === 'password'
                    ? 72
                    : f[0] === 'reference' || f[0] === 'code'
                      ? 60
                      : 200
                "
                :step="f[3] === 'integer' ? 1 : undefined"
                :autocomplete="
                  f[3] === 'password' ? 'new-password' : 'off'
                " /></label
          ></template>
        </div>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <footer>
          <button
            class="secondary"
            type="button"
            :disabled="busy"
            @click="modal = null"
          >
            {{ t("取消", "Cancel") }}</button
          ><button class="primary" :disabled="busy">
            {{
              busy
                ? t("正在保存", "Saving")
                : modal.kind === "command"
                  ? t("确认记录", "Confirm record")
                  : t("保存", "Save")
            }}
          </button>
        </footer>
      </form>
    </section>
  </div>
  <div v-if="contact" class="modal-backdrop" @click.self="contact = false">
    <section
      class="modal contact-modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('联系知华科技', 'Contact ZhuaTech')"
    >
      <header>
        <h2>{{ t("联系知华科技", "Contact ZhuaTech") }}</h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          @click="contact = false"
        >
          <X :size="17" />
        </button>
      </header>
      <img class="contact-logo" src="/brand/logo.jpg" alt="知华科技 LOGO" />
      <p>上海如静知华信息科技有限公司</p>
      <a
        href="https://www.zhuatech.cn/"
        target="_blank"
        rel="noopener noreferrer"
        >www.zhuatech.cn <ExternalLink :size="14"
      /></a>
      <p>
        {{
          t(
            "商业授权、定制开发、部署与系统集成",
            "Commercial licensing, customization, deployment & integration",
          )
        }}
      </p>
      <div class="qr-grid">
        <figure>
          <img
            src="/brand/wechat-zhuatech.png"
            alt="微信 zhuatech 官方二维码"
          />
          <figcaption>微信 zhuatech</figcaption>
        </figure>
        <figure>
          <img
            src="/brand/wechat-zhuatech2.png"
            alt="微信 zhuatech2 官方二维码"
          />
          <figcaption>微信 zhuatech2</figcaption>
        </figure>
      </div>
      <p class="license-note">
        {{
          t(
            "公开源码学习版／非商业源码版。商业使用须取得书面授权。",
            "Non-commercial source edition. Commercial use requires written permission.",
          )
        }}
      </p>
    </section>
  </div>
</template>
