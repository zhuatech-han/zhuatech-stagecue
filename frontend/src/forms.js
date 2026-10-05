// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 场次和后台的有限表单字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const fields = {
  productions: [
    ["reference", "制作编号", "Production reference"],
    ["name", "制作名称", "Production name"],
    ["departmentId", "负责部门", "Department", "id", "departments"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  books: [
    ["productionId", "制作", "Production", "id", "productions"],
    ["title", "提示本名称", "Cue book title"],
  ],
  cues: [
    ["bookId", "提示本", "Cue book", "id", "books"],
    ["sequence", "顺序号（1—9999）", "Sequence (1–9999)", "integer"],
    ["code", "提示编号", "Cue code"],
    ["kind", "提示类型", "Cue type", "select", "kinds"],
    ["triggerText", "提示触发语／动作标记", "Trigger / landmark", "textarea"],
    ["description", "执行说明", "Execution note", "textarea"],
    ["optional", "允许略过", "Optional", "boolean"],
    ["enabled", "列入此版", "Included", "boolean"],
  ],
  runs: [
    ["reference", "场次编号", "Run reference"],
    ["bookId", "已批准提示本", "Approved book", "id", "approvedBooks"],
    ["callerId", "提示员", "Caller", "id", "callers"],
    ["supervisorId", "独立监督员", "Supervisor", "id", "supervisors"],
    ["location", "场地", "Location"],
    [
      "plannedAt",
      "计划时间（上海时区）",
      "Planned time (Shanghai)",
      "datetime",
    ],
    ["mode", "场次性质", "Run mode", "select", "modes"],
  ],
  users: [
    ["username", "登录名", "Username"],
    ["displayName", "姓名", "Name"],
    [
      "password",
      "新密码（编辑时留空保留）",
      "New password (optional when editing)",
      "password",
    ],
    ["roleId", "角色", "Role", "id", "roles"],
    ["departmentId", "部门", "Department", "id", "departments"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  roles: [
    ["name", "角色名称", "Role name"],
    ["scope", "数据范围", "Data scope", "select", "scope"],
    ["permissions", "接口权限", "API permissions", "permissions"],
  ],
  departments: [["name", "部门名称", "Department name"]],
  menus: [
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
    [
      "permissionCode",
      "所需权限",
      "Required permission",
      "select",
      "permissions",
    ],
    ["position", "排序", "Order", "integer"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  permissions: [["name", "权限说明", "Permission description"]],
  dictionaries: [
    ["type", "字典类型", "Dictionary type"],
    ["code", "编码", "Code"],
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
  ],
  settings: [["value", "参数值", "Value"]],
};
/** 命令收集说明和可选指派账号，不让客户端填写实际时刻。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function commandFields(action) {
  const note = [
    "note",
    "核实说明／凭据编号",
    "Note / evidence reference",
    "textarea",
  ];
  return action === "assign"
    ? [
        ["operatorId", "指派操作员", "Assigned operator", "id", "operators"],
        note,
      ]
    : [note];
}
