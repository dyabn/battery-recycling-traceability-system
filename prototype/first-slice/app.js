const screens = [
  ["login", "登录"],
  ["dashboard", "首页或工作台"],
  ["batch-list", "回收批次列表"],
  ["batch-new", "新建回收批次"],
  ["batch-detail", "回收批次详情"],
  ["battery-register", "电池登记"],
  ["acceptance-list", "待验收列表"],
  ["acceptance-form", "验收登记"],
  ["supplement", "补充资料"],
  ["inbound-list", "待入库列表"],
  ["inbound-form", "入库办理"],
  ["inventory", "库存列表"],
  ["trace", "电池追溯详情"],
  ["exceptions", "无权限、重复编码和校验失败提示"],
];

const state = {
  role: "recycle",
  currentScreen: "login",
  batchNo: "BATCH-20260917-001",
  sourceType: "企业",
  sourceName: "华南回收合作方",
  handoverDate: "2026-09-17",
  batchStatus: "草稿",
  hasBattery: true,
  batteryValid: true,
  batteryStatus: "已登记",
  traceCode: "BAT-20260917-0001",
  originalCode: "ORI-NEW-001",
  duplicatePending: false,
  inventoryVisible: false,
  events: [
    "电池登记：生成系统追溯编码 BAT-20260917-0001",
  ],
  audits: [],
};

const nav = document.querySelector("#screenNav");
const title = document.querySelector("#screenTitle");
const content = document.querySelector("#screenContent");
const notice = document.querySelector("#notice");
const roleSelect = document.querySelector("#roleSelect");

const users = {
  recycle: { name: "recycle_user", label: "回收操作员" },
  warehouse: { name: "warehouse_user", label: "仓库管理员" },
  manager: { name: "manager_user", label: "业务主管" },
  admin: { name: "admin_user", label: "系统管理员" },
  viewer: { name: "viewer_user", label: "无权限用户" },
};

const warehouses = {
  "WH-001": { enabled: true },
  "WH-002": { enabled: false },
};

const locations = {
  "WH-001-A01-R01-L01": { warehouse: "WH-001", enabled: true },
  "WH-001-A01-R01-L99": { warehouse: "WH-001", enabled: false },
  "WH-002-A01-R01-L01": { warehouse: "WH-002", enabled: true },
};

function currentUser() {
  return users[state.role] || users.recycle;
}

function nowText() {
  return new Date().toLocaleString("zh-CN", { hour12: false });
}

function makeRecord(kind, name, object, statusChange, result, reason = "") {
  return {
    kind,
    name,
    object,
    operator: currentUser().name,
    time: nowText(),
    statusChange,
    result,
    reason,
  };
}

function showNotice(message, type = "info") {
  notice.className = `notice ${type}`;
  notice.textContent = message;
}

function setScreen(id) {
  state.currentScreen = id;
  title.textContent = screens.find((screen) => screen[0] === id)?.[1] || "原型";
  [...nav.querySelectorAll("button")].forEach((button) => {
    button.classList.toggle("active", button.dataset.screen === id);
  });
  render();
}

function addEvent(message) {
  if (typeof message === "string") {
    state.events.unshift(makeRecord("event", message.split("：")[0], state.traceCode, "", message));
    return;
  }
  state.events.unshift(message);
}

function addAudit(message) {
  if (typeof message === "string") {
    state.audits.unshift(makeRecord("audit", message.split("：")[0], state.traceCode, "", message, message));
    return;
  }
  state.audits.unshift(message);
}

function canInbound() {
  return state.role === "warehouse";
}

function hasPermission(action) {
  const matrix = {
    createBatch: ["recycle"],
    registerBattery: ["recycle"],
    submitBatch: ["recycle"],
    acceptance: ["recycle"],
    supplement: ["recycle"],
    inbound: ["warehouse"],
    permissionAdmin: ["admin"],
  };
  return matrix[action]?.includes(state.role) || false;
}

function deny(actionName, reason, object = state.traceCode) {
  addAudit(makeRecord("audit", "越权操作", object, "", `${actionName}被拒绝`, reason));
  showNotice(`${reason}，系统已记录审计日志。`, "danger");
}

function bootstrapNav() {
  nav.innerHTML = screens.map(([id, label]) => (
    `<button class="nav-item" type="button" data-screen="${id}">${label}</button>`
  )).join("");
  nav.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-screen]");
    if (!button) return;
    setScreen(button.dataset.screen);
  });
}

roleSelect.addEventListener("change", (event) => {
  state.role = event.target.value;
  showNotice(`当前角色已切换为：${event.target.options[event.target.selectedIndex].text}`, "info");
  render();
});

function resetDemo() {
  state.role = "recycle";
  roleSelect.value = "recycle";
  state.batchNo = "BATCH-20260917-001";
  state.sourceType = "企业";
  state.sourceName = "华南回收合作方";
  state.handoverDate = "2026-09-17";
  state.batchStatus = "草稿";
  state.hasBattery = true;
  state.batteryValid = true;
  state.batteryStatus = "已登记";
  state.traceCode = "BAT-20260917-0001";
  state.originalCode = "ORI-NEW-001";
  state.duplicatePending = false;
  state.inventoryVisible = false;
  state.events = [
    makeRecord("event", "电池登记", state.traceCode, "无档案 -> 已登记", "生成系统追溯编码"),
  ];
  state.audits = [];
}

function runHappyPath() {
  resetDemo();
  state.role = "warehouse";
  roleSelect.value = "warehouse";
  state.batchStatus = "已完成";
  state.batteryStatus = "在库";
  state.inventoryVisible = true;
  state.events = [
    { kind: "event", name: "入库", object: state.traceCode, operator: "warehouse_user", time: nowText(), statusChange: "已验收待入库 -> 在库", result: "WH-001 / WH-001-A01-R01-L01，生成库存记录" },
    { kind: "event", name: "验收通过", object: state.traceCode, operator: "recycle_user", time: nowText(), statusChange: "待验收 -> 已验收待入库", result: "人工验收结论为通过" },
    { kind: "event", name: "提交验收", object: state.batchNo, operator: "recycle_user", time: nowText(), statusChange: "已登记 -> 待验收", result: "批次整体校验通过" },
    { kind: "event", name: "电池登记", object: state.traceCode, operator: "recycle_user", time: nowText(), statusChange: "无档案 -> 已登记", result: "生成系统追溯编码" },
  ];
  showNotice("正常入库闭环已演示：电池完成登记、验收、入库并在库存中可见。", "success");
  setScreen("trace");
}

function runDuplicateCode() {
  resetDemo();
  state.originalCode = "ORI-DUP-001";
  state.batteryStatus = "疑似重复待核实";
  state.batteryValid = false;
  state.duplicatePending = true;
  state.inventoryVisible = false;
  addAudit(makeRecord("audit", "重复编码识别", "ORI-DUP-001", "", "禁止直接建立新的有效档案", "原始编码已存在有效档案"));
  showNotice("原始编码疑似重复：禁止直接建立新的有效档案。", "warning");
  setScreen("battery-register");
}

function runBatchFailure() {
  resetDemo();
  state.batchStatus = "草稿";
  state.sourceName = "";
  state.handoverDate = "";
  state.hasBattery = false;
  state.batteryStatus = "未登记";
  state.inventoryVisible = false;
  addAudit(makeRecord("audit", "批次提交失败", state.batchNo, "", "拒绝提交验收", "来源必填信息缺失或批次为空"));
  showNotice("批次提交失败：请补充来源类型、来源主体名称、交接日期，并至少加入一个有效电池包。", "danger");
  setScreen("batch-new");
}

function runSupplement() {
  resetDemo();
  state.batchStatus = "验收处理中";
  state.batteryStatus = "待补充资料";
  state.inventoryVisible = false;
  addEvent(makeRecord("event", "资料不足", state.traceCode, "待验收 -> 待补充资料", "需要补充交接证明"));
  showNotice("资料不足场景：必须填写补充资料说明，补充后可重新提交验收。", "warning");
  setScreen("supplement");
}

function runRejectedAcceptance() {
  resetDemo();
  state.batchStatus = "已完成";
  state.batteryStatus = "验收不通过";
  state.inventoryVisible = false;
  addEvent(makeRecord("event", "验收不通过", state.traceCode, "待验收 -> 验收不通过", "外观严重破损，不能入库"));
  showNotice("验收不通过：电池不能办理正常入库。", "danger");
  setScreen("acceptance-form");
}

function runInvalidLocation() {
  resetDemo();
  state.role = "warehouse";
  roleSelect.value = "warehouse";
  state.batteryStatus = "已验收待入库";
  state.batchStatus = "已完成";
  state.inventoryVisible = false;
  addAudit(makeRecord("audit", "入库失败", state.traceCode, "", "拒绝入库", "选择停用仓库或停用库位"));
  showNotice("入库失败：请选择启用状态的仓库和库位。", "danger");
  setScreen("inbound-form");
}

function runUnauthorized() {
  resetDemo();
  state.role = "viewer";
  roleSelect.value = "viewer";
  state.batteryStatus = "已验收待入库";
  state.batchStatus = "已完成";
  state.inventoryVisible = false;
  deny("入库", "当前账号无权执行入库");
  setScreen("exceptions");
}

function runDeleteProtection() {
  resetDemo();
  state.role = "manager";
  roleSelect.value = "manager";
  addAudit(makeRecord("audit", "删除保护", "验收记录 ACC-20260917-001", "", "拒绝物理删除", "已生效记录不能直接删除"));
  showNotice("已生效记录不能直接删除，请通过后续更正或撤销流程处理。", "danger");
  setScreen("exceptions");
}

function submitInbound(valid = true) {
  if (!canInbound()) {
    runUnauthorized();
    return;
  }
  if (state.batteryStatus !== "已验收待入库") {
    addAudit(`入库失败：当前状态为 ${state.batteryStatus}`);
    showNotice("只有已验收待入库的电池可以办理入库。", "danger");
    return;
  }
  if (!valid) {
    runInvalidLocation();
    return;
  }
  state.batteryStatus = "在库";
  state.batchStatus = "已完成";
  state.inventoryVisible = true;
  addEvent("入库：WH-001 / WH-001-A01-R01-L01，状态 已验收待入库 -> 在库");
  showNotice("入库成功：已生成入库记录和有效库存记录。", "success");
  setScreen("inventory");
}

function resetAndStay() {
  resetDemo();
  showNotice("原型状态已重置，库存、异常和审计记录已清空。", "info");
  setScreen("dashboard");
}

function saveBatchFromForm() {
  if (!hasPermission("createBatch")) {
    deny("创建回收批次", "当前角色不能创建回收批次", state.batchNo);
    return;
  }
  const sourceType = document.querySelector("#sourceType").value.trim();
  const sourceName = document.querySelector("#sourceName").value.trim();
  const handoverDate = document.querySelector("#handoverDate").value.trim();
  if (!sourceType || !sourceName || !handoverDate) {
    addAudit(makeRecord("audit", "来源信息校验失败", state.batchNo, "", "拒绝保存批次", "来源类型、来源主体名称或交接日期缺失"));
    showNotice("请补充来源类型、来源主体名称和交接日期。", "danger");
    return;
  }
  state.sourceType = sourceType;
  state.sourceName = sourceName;
  state.handoverDate = handoverDate;
  state.batchStatus = "草稿";
  showNotice("回收批次已保存，选填字段为空不阻止流程。", "success");
  setScreen("batch-detail");
}

function submitBatchFromDetail() {
  if (!hasPermission("submitBatch")) {
    deny("提交验收", "当前角色不能提交批次验收", state.batchNo);
    return;
  }
  if (!state.sourceType || !state.sourceName || !state.handoverDate) {
    addAudit(makeRecord("audit", "批次提交失败", state.batchNo, "", "拒绝提交验收", "来源必填信息缺失"));
    showNotice("请补充来源类型、来源主体名称和交接日期后再提交验收。", "danger");
    return;
  }
  if (!state.hasBattery) {
    addAudit(makeRecord("audit", "批次提交失败", state.batchNo, "", "拒绝提交验收", "批次为空"));
    showNotice("批次至少需要一个有效电池包。", "danger");
    return;
  }
  if (!state.batteryValid || state.duplicatePending) {
    addAudit(makeRecord("audit", "批次提交失败", state.batchNo, "", "拒绝提交验收", "存在未核实或无效电池"));
    showNotice("批次内存在未完成登记或未核实的电池，不能提交验收。", "danger");
    return;
  }
  state.batchStatus = "待验收";
  state.batteryStatus = "待验收";
  addEvent(makeRecord("event", "提交验收", state.batchNo, "已登记 -> 待验收", "批次整体校验通过"));
  showNotice("批次提交验收成功，批次内有效电池进入待验收。", "success");
  render();
}

function saveBatteryFromForm() {
  if (!hasPermission("registerBattery")) {
    deny("登记电池", "当前角色不能登记电池");
    return;
  }
  const originalCode = document.querySelector("#originalCodeInput").value.trim();
  const batteryChemistry = document.querySelector("#batteryChemistry").value.trim();
  if (!batteryChemistry) {
    showNotice("请选择电池体系；未知也可作为有效选项。", "danger");
    return;
  }
  if (originalCode === "ORI-DUP-001") {
    state.originalCode = originalCode;
    state.batteryStatus = "疑似重复待核实";
    state.batteryValid = false;
    state.duplicatePending = true;
    addAudit(makeRecord("audit", "重复编码识别", originalCode, "", "禁止直接建立新的有效档案", "原始编码已存在有效档案"));
    showNotice("原始编码已存在，请先在重复编码核实区域处理。", "warning");
    render();
    return;
  }
  state.originalCode = originalCode;
  state.hasBattery = true;
  state.batteryValid = true;
  state.duplicatePending = false;
  state.batteryStatus = "已登记";
  addEvent(makeRecord("event", "电池登记", state.traceCode, "无档案 -> 已登记", `生成系统追溯编码，电池体系：${batteryChemistry}`));
  showNotice("电池档案已建立，系统追溯编码全局唯一。", "success");
  setScreen("batch-detail");
}

function resolveDuplicateAsSame() {
  state.traceCode = "BAT-EXISTING-0008";
  state.batteryStatus = "已登记";
  state.batteryValid = true;
  state.duplicatePending = false;
  state.hasBattery = true;
  addAudit(makeRecord("audit", "重复编码核实", "ORI-DUP-001", "", "确认为同一电池，使用原档案 BAT-EXISTING-0008", "人工核实实物与原档案一致"));
  showNotice("已确认为同一电池，使用原档案继续处理。", "success");
  setScreen("batch-detail");
}

function resolveDuplicateAsDifferent() {
  const reason = document.querySelector("#duplicateReason")?.value.trim();
  if (!reason) {
    showNotice("核实为不同电池时必须填写重复原因。", "danger");
    return;
  }
  state.traceCode = "BAT-20260917-0002";
  state.batteryStatus = "已登记";
  state.batteryValid = true;
  state.duplicatePending = false;
  state.hasBattery = true;
  addAudit(makeRecord("audit", "重复编码核实", "ORI-DUP-001", "疑似重复待核实 -> 已登记", "确认为不同电池，生成新系统追溯编码 BAT-20260917-0002", reason));
  addEvent(makeRecord("event", "电池登记", state.traceCode, "疑似重复待核实 -> 已登记", "人工核实后生成新系统追溯编码"));
  showNotice("已确认为不同电池，系统生成新的系统追溯编码并记录原因。", "success");
  setScreen("batch-detail");
}

function saveAcceptanceFromForm() {
  if (!hasPermission("acceptance")) {
    deny("登记验收", "当前角色不能登记验收结果");
    return;
  }
  const result = document.querySelector("#acceptanceResult").value;
  const note = document.querySelector("#acceptanceNote").value.trim();
  if (!result) {
    showNotice("验收必须给出明确结果。", "danger");
    return;
  }
  state.batchStatus = "验收处理中";
  if (result === "待补充资料" && !note) {
    showNotice("请填写需要补充资料的说明。", "danger");
    return;
  }
  if (result === "不通过" && !note) {
    showNotice("请填写验收不通过原因。", "danger");
    return;
  }
  if (result === "通过") {
    state.batteryStatus = "已验收待入库";
    state.batchStatus = "已完成";
    addEvent(makeRecord("event", "验收通过", state.traceCode, "待验收 -> 已验收待入库", "人工验收结论为通过"));
    showNotice("验收通过，电池进入已验收待入库；当前单电池批次已完成验收。", "success");
    setScreen("inbound-list");
    return;
  }
  if (result === "待补充资料") {
    state.batteryStatus = "待补充资料";
    state.batchStatus = "验收处理中";
    addEvent(makeRecord("event", "资料不足", state.traceCode, "待验收 -> 待补充资料", note));
    showNotice("资料不足，批次保持验收处理中。", "warning");
    setScreen("supplement");
    return;
  }
  state.batteryStatus = "验收不通过";
  state.batchStatus = "已完成";
  addEvent(makeRecord("event", "验收不通过", state.traceCode, "待验收 -> 验收不通过", note));
  showNotice("验收不通过，电池不能办理正常入库；当前单电池批次已完成验收。", "danger");
  render();
}

function supplementFromForm() {
  if (!hasPermission("supplement")) {
    deny("补充资料", "当前角色不能补充验收资料");
    return;
  }
  const note = document.querySelector("#supplementNote").value.trim();
  if (!note) {
    showNotice("请填写补充资料说明。", "danger");
    return;
  }
  state.batteryStatus = "待验收";
  state.batchStatus = "验收处理中";
  addEvent(makeRecord("event", "资料补充", state.traceCode, "待补充资料 -> 待验收", note));
  showNotice("资料已补充，可重新提交验收。", "success");
  setScreen("acceptance-list");
}

function submitInboundFromForm() {
  if (!hasPermission("inbound")) {
    deny("入库", "当前账号无权执行入库");
    return;
  }
  if (state.batteryStatus !== "已验收待入库") {
    addAudit(makeRecord("audit", "入库失败", state.traceCode, "", "拒绝入库", `当前状态为 ${state.batteryStatus}`));
    showNotice("只有已验收待入库的电池可以办理入库。", "danger");
    return;
  }
  const warehouseCode = document.querySelector("#warehouseSelect").value;
  const locationCode = document.querySelector("#locationSelect").value;
  const warehouse = warehouses[warehouseCode];
  const location = locations[locationCode];
  if (!warehouse?.enabled || !location?.enabled) {
    addAudit(makeRecord("audit", "入库失败", state.traceCode, "", "拒绝入库", "选择了停用仓库或停用库位"));
    showNotice("请选择启用状态的仓库和库位。", "danger");
    return;
  }
  if (location.warehouse !== warehouseCode) {
    addAudit(makeRecord("audit", "入库失败", state.traceCode, "", "拒绝入库", "库位不属于所选仓库"));
    showNotice("所选库位不属于当前仓库。", "danger");
    return;
  }
  state.batteryStatus = "在库";
  state.batchStatus = "已完成";
  state.inventoryVisible = true;
  addEvent(makeRecord("event", "入库", state.traceCode, "已验收待入库 -> 在库", `${warehouseCode} / ${locationCode}，生成入库记录和有效库存记录`));
  showNotice("入库成功：已生成入库记录和有效库存记录。", "success");
  setScreen("inventory");
}

function runPermissionMatrixDemo() {
  resetDemo();
  const checks = [
    ["recycle", "入库", "回收操作员不能办理入库"],
    ["warehouse", "验收登记", "仓库管理员不能登记验收"],
    ["manager", "创建批次", "业务主管只能查看，不能代替操作员办理业务"],
    ["admin", "验收或入库", "系统管理员不能代替业务角色完成验收或入库"],
    ["viewer", "业务操作", "无权限用户不能执行任何业务操作"],
    ["admin", "权限管理与审计", "系统管理员可以管理权限并查看审计"],
  ];
  state.audits = checks.map(([role, object, result]) => ({
    kind: "audit",
    name: "权限矩阵验证",
    object,
    operator: users[role].name,
    time: nowText(),
    statusChange: "",
    result,
    reason: role === "admin" && object === "权限管理与审计" ? "具备系统管理权限" : "角色职责边界限制",
  }));
  showNotice("权限矩阵测试已生成：覆盖回收操作员、仓库管理员、业务主管、系统管理员和无权限用户。", "success");
  setScreen("exceptions");
}

function renderTimeline() {
  const rows = [...state.events, ...state.audits];
  if (!rows.length) {
    return `<p class="muted">暂无生命周期事件或审计记录。</p>`;
  }
  return `
    <div class="timeline">
      ${rows.map((item) => `
        <div class="event ${item.kind === "audit" ? "audit" : ""}">
          <strong>${item.name}</strong>
          <dl>
            <div><dt>对象</dt><dd>${item.object}</dd></div>
            <div><dt>操作人</dt><dd>${item.operator}</dd></div>
            <div><dt>操作时间</dt><dd>${item.time}</dd></div>
            ${item.statusChange ? `<div><dt>状态变化</dt><dd>${item.statusChange}</dd></div>` : ""}
            <div><dt>处理结果</dt><dd>${item.result}</dd></div>
            ${item.reason ? `<div><dt>拒绝原因</dt><dd>${item.reason}</dd></div>` : ""}
          </dl>
        </div>
      `).join("")}
    </div>
  `;
}

function renderStatusBox() {
  return `
    <div class="grid">
      <div class="metric col-3"><span>批次状态</span><strong>${state.batchStatus}</strong></div>
      <div class="metric col-3"><span>电池状态</span><strong>${state.batteryStatus}</strong></div>
      <div class="metric col-3"><span>追溯编码</span><strong>${state.traceCode}</strong></div>
      <div class="metric col-3"><span>原始编码</span><strong>${state.originalCode || "空"}</strong></div>
    </div>
  `;
}

function screenLogin() {
  return `
    <h2>登录</h2>
    <div class="grid">
      <div class="box col-6">
        <label class="field"><span>账号</span><input value="recycle_user" /></label>
        <label class="field"><span>角色</span>
          <select onchange="document.querySelector('#roleSelect').value=this.value; document.querySelector('#roleSelect').dispatchEvent(new Event('change'))">
            <option value="recycle">回收操作员</option>
            <option value="warehouse">仓库管理员</option>
            <option value="manager">业务主管</option>
            <option value="admin">系统管理员</option>
            <option value="viewer">无入库权限用户</option>
          </select>
        </label>
        <button type="button" onclick="setScreen('dashboard')">进入工作台</button>
      </div>
      <div class="hint-panel col-6">
        <strong>评审关注</strong>
        <p>登录只用于验证角色权限差异，不代表正式认证方案。</p>
      </div>
    </div>
  `;
}

function screenDashboard() {
  return `
    <h2>首页或工作台</h2>
    ${renderStatusBox()}
    <h3>场景演示</h3>
    <div class="toolbar">
      <button type="button" onclick="runHappyPath()">正常入库闭环</button>
      <button type="button" class="secondary" onclick="resetAndStay()">重置原型</button>
      <button type="button" class="secondary" onclick="runDuplicateCode()">重复原始编码</button>
      <button type="button" class="secondary" onclick="runBatchFailure()">批次提交失败</button>
      <button type="button" class="secondary" onclick="runSupplement()">待补充资料</button>
      <button type="button" class="secondary" onclick="runRejectedAcceptance()">验收不通过</button>
      <button type="button" class="secondary" onclick="runInvalidLocation()">无效仓库或库位</button>
      <button type="button" class="secondary" onclick="runUnauthorized()">越权操作</button>
      <button type="button" class="secondary" onclick="runPermissionMatrixDemo()">权限矩阵测试</button>
      <button type="button" class="danger" onclick="runDeleteProtection()">已生效记录禁止删除</button>
    </div>
    <div class="flow">
      <span>创建批次</span><span>登记电池</span><span>提交验收</span><span>验收登记</span><span>入库办理</span><span>库存可见</span>
    </div>
  `;
}

function screenBatchList() {
  return `
    <h2>回收批次列表</h2>
    <div class="toolbar"><button onclick="setScreen('batch-new')">新建回收批次</button></div>
    <table>
      <thead><tr><th>批次编号</th><th>来源主体</th><th>交接日期</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr><td>${state.batchNo}</td><td>${state.sourceName || "未填写"}</td><td>${state.handoverDate || "未填写"}</td><td><span class="tag">${state.batchStatus}</span></td><td><button class="secondary" onclick="setScreen('batch-detail')">查看</button></td></tr>
      </tbody>
    </table>
  `;
}

function screenBatchNew() {
  return `
    <h2>新建回收批次</h2>
    <div class="form-row">
      <label class="field"><span>批次编号</span><input value="系统自动生成" disabled /></label>
      <label class="field"><span>来源类型 *</span><select id="sourceType"><option ${state.sourceType === "企业" ? "selected" : ""}>企业</option><option ${state.sourceType === "个人" ? "selected" : ""}>个人</option><option ${state.sourceType === "其他" ? "selected" : ""}>其他</option></select></label>
    </div>
    <div class="form-row">
      <label class="field"><span>来源主体名称 *</span><input id="sourceName" value="${state.sourceName}" /></label>
      <label class="field"><span>交接日期 *</span><input id="handoverDate" value="${state.handoverDate}" /></label>
    </div>
    <div class="form-row">
      <label class="field"><span>关联单据号</span><input placeholder="选填" /></label>
      <label class="field"><span>交接地点</span><input placeholder="选填" /></label>
    </div>
    <div class="form-row">
      <label class="field"><span>交接人员</span><input placeholder="选填" /></label>
      <label class="field"><span>附件</span><input value="选填：交接凭证" disabled /></label>
    </div>
    <button onclick="saveBatchFromForm()">保存批次</button>
    <button class="secondary" onclick="document.querySelector('#sourceName').value=''; document.querySelector('#handoverDate').value=''; saveBatchFromForm()">演示来源缺失提交失败</button>
  `;
}

function screenBatchDetail() {
  return `
    <h2>回收批次详情</h2>
    ${renderStatusBox()}
    <div class="toolbar">
      <button onclick="setScreen('battery-register')">登记电池</button>
      <button class="secondary" onclick="submitBatchFromDetail()">提交验收</button>
      <button class="secondary" onclick="state.hasBattery=false; submitBatchFromDetail()">演示空批次提交失败</button>
    </div>
    <table>
      <thead><tr><th>系统追溯编码</th><th>原始编码</th><th>电池体系</th><th>状态</th></tr></thead>
      <tbody>
        ${state.hasBattery ? `<tr><td>${state.traceCode}</td><td>${state.originalCode}</td><td>未知</td><td><span class="tag">${state.batteryStatus}</span></td></tr>` : `<tr><td colspan="4" class="muted">当前批次不包含任何有效电池包。</td></tr>`}
      </tbody>
    </table>
  `;
}

function screenBatteryRegister() {
  const duplicatePanel = state.duplicatePending ? `
    <div class="box col-12">
      <h3>重复编码核实</h3>
      <p class="muted">原始编码 ORI-DUP-001 已存在有效档案。请人工核实后选择处理分支。</p>
      <label class="field"><span>重复原因</span><textarea id="duplicateReason" placeholder="核实为不同电池时必填">上游重复贴码，经人工核实为不同电池。</textarea></label>
      <div class="toolbar">
        <button class="secondary" onclick="resolveDuplicateAsSame()">确认为同一电池，使用原档案</button>
        <button onclick="resolveDuplicateAsDifferent()">确认为不同电池，生成新追溯编码</button>
      </div>
    </div>
  ` : "";
  return `
    <h2>电池登记</h2>
    <div class="grid">
      <div class="box col-12">
        <div class="form-row">
          <label class="field"><span>系统追溯编码</span><input value="保存后自动生成" disabled /></label>
          <label class="field"><span>原始编码</span><input id="originalCodeInput" value="${state.originalCode}" /></label>
        </div>
        <div class="form-row">
          <label class="field"><span>电池类型</span><input value="电池包" disabled /></label>
          <label class="field"><span>电池体系 *</span><select id="batteryChemistry"><option>未知</option><option>磷酸铁锂</option><option>三元锂</option></select></label>
        </div>
        <div class="form-row">
          <label class="field"><span>电池型号</span><input placeholder="选填" /></label>
          <label class="field"><span>生产企业</span><input placeholder="选填" /></label>
        </div>
        <div class="toolbar">
          <button onclick="saveBatteryFromForm()">保存电池档案</button>
          <button class="secondary" onclick="document.querySelector('#originalCodeInput').value='ORI-DUP-001'; saveBatteryFromForm()">填入重复原始编码并保存</button>
        </div>
      </div>
      ${duplicatePanel}
    </div>
  `;
}

function screenAcceptanceList() {
  return `
    <h2>待验收列表</h2>
    <table>
      <thead><tr><th>追溯编码</th><th>所属批次</th><th>状态</th><th>操作</th></tr></thead>
      <tbody><tr><td>${state.traceCode}</td><td>BATCH-20260917-001</td><td><span class="tag">${state.batteryStatus}</span></td><td><button class="secondary" onclick="setScreen('acceptance-form')">验收登记</button></td></tr></tbody>
    </table>
  `;
}

function screenAcceptanceForm() {
  return `
    <h2>验收登记</h2>
    <div class="form-row">
      <label class="field"><span>验收结果 *</span><select id="acceptanceResult"><option value="">请选择</option><option>通过</option><option>待补充资料</option><option>不通过</option></select></label>
      <label class="field"><span>编码及身份核对情况 *</span><input value="编码与实物一致" /></label>
    </div>
    <div class="form-row">
      <label class="field"><span>外观情况 *</span><input value="外观无明显破损" /></label>
      <label class="field"><span>资料完整情况 *</span><input value="资料完整" /></label>
    </div>
    <label class="field"><span>验收说明</span><textarea id="acceptanceNote">人工验收结论记录</textarea></label>
    <div class="toolbar">
      <button onclick="saveAcceptanceFromForm()">保存验收结果</button>
      <button class="secondary" onclick="document.querySelector('#acceptanceResult').value=''; saveAcceptanceFromForm()">演示验收结果为空</button>
      <button class="secondary" onclick="document.querySelector('#acceptanceResult').value='不通过'; document.querySelector('#acceptanceNote').value='外观严重破损'; saveAcceptanceFromForm()">演示验收不通过</button>
    </div>
  `;
}

function screenSupplement() {
  return `
    <h2>补充资料</h2>
    <label class="field"><span>补充资料说明 *</span><textarea id="supplementNote">补充交接证明和照片附件。</textarea></label>
    <label class="field"><span>附件</span><input value="选填：supplement.pdf" /></label>
    <button onclick="supplementFromForm()">补充并重新提交</button>
  `;
}

function screenInboundList() {
  return `
    <h2>待入库列表</h2>
    <table>
      <thead><tr><th>追溯编码</th><th>验收结果</th><th>当前状态</th><th>操作</th></tr></thead>
      <tbody><tr><td>${state.traceCode}</td><td>通过</td><td><span class="tag ok">${state.batteryStatus}</span></td><td><button class="secondary" onclick="setScreen('inbound-form')">办理入库</button></td></tr></tbody>
    </table>
  `;
}

function screenInboundForm() {
  return `
    <h2>入库办理</h2>
    ${renderStatusBox()}
    <div class="form-row">
      <label class="field"><span>仓库 *</span><select id="warehouseSelect"><option value="WH-001">WH-001 主仓库（启用）</option><option value="WH-002">WH-002 停用仓库</option></select></label>
      <label class="field"><span>库位 *</span><select id="locationSelect"><option value="WH-001-A01-R01-L01">WH-001-A01-R01-L01（启用）</option><option value="WH-001-A01-R01-L99">WH-001-A01-R01-L99（停用）</option><option value="WH-002-A01-R01-L01">WH-002-A01-R01-L01（属于 WH-002）</option></select></label>
    </div>
    <div class="toolbar">
      <button onclick="submitInboundFromForm()">确认入库</button>
      <button class="secondary" onclick="document.querySelector('#warehouseSelect').value='WH-002'; submitInboundFromForm()">演示停用仓库</button>
      <button class="secondary" onclick="document.querySelector('#locationSelect').value='WH-002-A01-R01-L01'; submitInboundFromForm()">演示库位归属不一致</button>
      <button class="secondary" onclick="runUnauthorized()">演示越权入库</button>
    </div>
  `;
}

function screenInventory() {
  return `
    <h2>库存列表</h2>
    <label class="field"><span>按追溯编码查询</span><input value="${state.traceCode}" /></label>
    <table>
      <thead><tr><th>追溯编码</th><th>当前责任企业</th><th>仓库</th><th>库位</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        ${state.inventoryVisible ? `<tr><td>${state.traceCode}</td><td>华东动力电池回收有限公司</td><td>WH-001</td><td>WH-001-A01-R01-L01</td><td><span class="tag ok">在库</span></td><td><button class="secondary" onclick="setScreen('trace')">追溯</button></td></tr>` : `<tr><td colspan="6" class="muted">当前还没有有效库存记录。</td></tr>`}
      </tbody>
    </table>
  `;
}

function screenTrace() {
  return `
    <h2>电池追溯详情</h2>
    ${renderStatusBox()}
    <h3>生命周期时间线和审计摘要</h3>
    ${renderTimeline()}
  `;
}

function screenExceptions() {
  return `
    <h2>无权限、重复编码和校验失败提示</h2>
    <div class="grid">
      <div class="box col-6"><span class="tag warn">重复编码</span><p>原始编码已存在，请先完成人工核实。核实为同一电池时使用原档案；核实为不同电池时生成新的系统追溯编码并记录原因。</p><button class="secondary" onclick="runDuplicateCode()">演示</button></div>
      <div class="box col-6"><span class="tag fail">校验失败</span><p>来源必填信息缺失、空批次、无效仓库或库位都会阻止流程继续，状态保持不变。</p><button class="secondary" onclick="runBatchFailure()">演示</button></div>
      <div class="box col-6"><span class="tag fail">越权操作</span><p>无入库权限用户尝试入库会被拒绝，并记录审计日志。</p><button class="secondary" onclick="runUnauthorized()">演示</button></div>
      <div class="box col-6"><span class="tag fail">删除保护</span><p>已生效记录不能物理删除或直接覆盖，后续通过更正或撤销流程处理。</p><button class="danger" onclick="runDeleteProtection()">演示</button></div>
    </div>
    <h3>审计日志</h3>
    ${renderTimeline()}
  `;
}

function render() {
  const screenMap = {
    login: screenLogin,
    dashboard: screenDashboard,
    "batch-list": screenBatchList,
    "batch-new": screenBatchNew,
    "batch-detail": screenBatchDetail,
    "battery-register": screenBatteryRegister,
    "acceptance-list": screenAcceptanceList,
    "acceptance-form": screenAcceptanceForm,
    supplement: screenSupplement,
    "inbound-list": screenInboundList,
    "inbound-form": screenInboundForm,
    inventory: screenInventory,
    trace: screenTrace,
    exceptions: screenExceptions,
  };
  content.innerHTML = screenMap[state.currentScreen]();
}

bootstrapNav();
setScreen("login");
