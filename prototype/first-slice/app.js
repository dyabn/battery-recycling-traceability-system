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
  batchStatus: "草稿",
  batteryStatus: "已登记",
  traceCode: "BAT-20260917-0001",
  originalCode: "ORI-NEW-001",
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
  state.events.unshift(message);
}

function addAudit(message) {
  state.audits.unshift(message);
}

function canInbound() {
  return state.role === "warehouse";
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
  state.batchStatus = "草稿";
  state.batteryStatus = "已登记";
  state.traceCode = "BAT-20260917-0001";
  state.originalCode = "ORI-NEW-001";
  state.inventoryVisible = false;
  state.events = ["电池登记：生成系统追溯编码 BAT-20260917-0001"];
  state.audits = [];
}

function runHappyPath() {
  resetDemo();
  state.batchStatus = "已完成";
  state.batteryStatus = "在库";
  state.inventoryVisible = true;
  state.events = [
    "入库：WH-001 / WH-001-A01-R01-L01，状态 已验收待入库 -> 在库",
    "验收通过：状态 待验收 -> 已验收待入库",
    "提交验收：批次 BATCH-20260917-001，状态 已登记 -> 待验收",
    "电池登记：生成系统追溯编码 BAT-20260917-0001",
  ];
  showNotice("正常入库闭环已演示：电池完成登记、验收、入库并在库存中可见。", "success");
  setScreen("trace");
}

function runDuplicateCode() {
  state.originalCode = "ORI-DUP-001";
  state.batteryStatus = "疑似重复待核实";
  addAudit("重复编码处理：ORI-DUP-001 已存在，要求人工核实原档案");
  showNotice("原始编码疑似重复：禁止直接建立新的有效档案。", "warning");
  setScreen("exceptions");
}

function runBatchFailure() {
  state.batchStatus = "草稿";
  addAudit("批次提交失败：缺少来源必填信息或批次为空");
  showNotice("批次提交失败：请补充来源类型、来源主体名称、交接日期，并至少加入一个有效电池包。", "danger");
  setScreen("batch-detail");
}

function runSupplement() {
  state.batchStatus = "验收处理中";
  state.batteryStatus = "待补充资料";
  addEvent("资料不足：需要补充交接证明，状态 待验收 -> 待补充资料");
  showNotice("资料不足场景：必须填写补充资料说明，补充后可重新提交验收。", "warning");
  setScreen("supplement");
}

function runRejectedAcceptance() {
  state.batchStatus = "已完成";
  state.batteryStatus = "验收不通过";
  addEvent("验收不通过：外观严重破损，状态 待验收 -> 验收不通过");
  showNotice("验收不通过：电池不能办理正常入库。", "danger");
  setScreen("acceptance-form");
}

function runInvalidLocation() {
  state.batteryStatus = "已验收待入库";
  addAudit("入库失败：选择停用仓库 WH-002 或停用库位 WH-001-A01-R01-L99");
  showNotice("入库失败：请选择启用状态的仓库和库位。", "danger");
  setScreen("inbound-form");
}

function runUnauthorized() {
  state.role = "viewer";
  roleSelect.value = "viewer";
  addAudit("越权操作：无入库权限用户尝试确认入库，被系统拒绝");
  showNotice("当前账号无权执行入库，系统已记录审计日志。", "danger");
  setScreen("exceptions");
}

function runDeleteProtection() {
  addAudit("删除保护：用户尝试删除已生效验收记录，被系统拒绝");
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

function renderTimeline() {
  return `
    <div class="timeline">
      ${state.events.map((event) => `<div class="event">${event}</div>`).join("")}
      ${state.audits.map((audit) => `<div class="event"><strong>审计</strong>：${audit}</div>`).join("")}
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
      <button type="button" class="secondary" onclick="runDuplicateCode()">重复原始编码</button>
      <button type="button" class="secondary" onclick="runBatchFailure()">批次提交失败</button>
      <button type="button" class="secondary" onclick="runSupplement()">待补充资料</button>
      <button type="button" class="secondary" onclick="runRejectedAcceptance()">验收不通过</button>
      <button type="button" class="secondary" onclick="runInvalidLocation()">无效仓库或库位</button>
      <button type="button" class="secondary" onclick="runUnauthorized()">越权操作</button>
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
        <tr><td>BATCH-20260917-001</td><td>华南回收合作方</td><td>2026-09-17</td><td><span class="tag">${state.batchStatus}</span></td><td><button class="secondary" onclick="setScreen('batch-detail')">查看</button></td></tr>
      </tbody>
    </table>
  `;
}

function screenBatchNew() {
  return `
    <h2>新建回收批次</h2>
    <div class="form-row">
      <label class="field"><span>批次编号</span><input value="系统自动生成" disabled /></label>
      <label class="field"><span>来源类型 *</span><select><option>企业</option><option>个人</option><option>其他</option></select></label>
    </div>
    <div class="form-row">
      <label class="field"><span>来源主体名称 *</span><input value="华南回收合作方" /></label>
      <label class="field"><span>交接日期 *</span><input value="2026-09-17" /></label>
    </div>
    <div class="form-row">
      <label class="field"><span>关联单据号</span><input placeholder="选填" /></label>
      <label class="field"><span>交接地点</span><input placeholder="选填" /></label>
    </div>
    <div class="form-row">
      <label class="field"><span>交接人员</span><input placeholder="选填" /></label>
      <label class="field"><span>附件</span><input value="选填：交接凭证" disabled /></label>
    </div>
    <button onclick="state.batchStatus='草稿'; showNotice('回收批次已保存，关联单据号为空也允许保存。','success'); setScreen('batch-detail')">保存批次</button>
    <button class="secondary" onclick="runBatchFailure()">演示来源缺失提交失败</button>
  `;
}

function screenBatchDetail() {
  return `
    <h2>回收批次详情</h2>
    ${renderStatusBox()}
    <div class="toolbar">
      <button onclick="setScreen('battery-register')">登记电池</button>
      <button class="secondary" onclick="state.batchStatus='待验收'; state.batteryStatus='待验收'; addEvent('提交验收：批次整体校验通过，状态 已登记 -> 待验收'); showNotice('批次提交验收成功。','success'); render()">提交验收</button>
      <button class="secondary" onclick="runBatchFailure()">演示提交失败</button>
    </div>
    <table>
      <thead><tr><th>系统追溯编码</th><th>原始编码</th><th>电池体系</th><th>状态</th></tr></thead>
      <tbody><tr><td>${state.traceCode}</td><td>${state.originalCode}</td><td>未知</td><td><span class="tag">${state.batteryStatus}</span></td></tr></tbody>
    </table>
  `;
}

function screenBatteryRegister() {
  return `
    <h2>电池登记</h2>
    <div class="form-row">
      <label class="field"><span>系统追溯编码</span><input value="保存后自动生成" disabled /></label>
      <label class="field"><span>原始编码</span><input id="originalCodeInput" value="${state.originalCode}" /></label>
    </div>
    <div class="form-row">
      <label class="field"><span>电池类型</span><input value="电池包" disabled /></label>
      <label class="field"><span>电池体系 *</span><select><option>未知</option><option>磷酸铁锂</option><option>三元锂</option></select></label>
    </div>
    <div class="form-row">
      <label class="field"><span>电池型号</span><input placeholder="选填" /></label>
      <label class="field"><span>生产企业</span><input placeholder="选填" /></label>
    </div>
    <div class="toolbar">
      <button onclick="const v=document.querySelector('#originalCodeInput').value; if(v==='ORI-DUP-001'){runDuplicateCode()}else{state.originalCode=v; state.batteryStatus='已登记'; addEvent('电池登记：生成系统追溯编码 '+state.traceCode); showNotice('电池档案已建立，系统追溯编码全局唯一。','success'); setScreen('batch-detail')}">保存电池档案</button>
      <button class="secondary" onclick="document.querySelector('#originalCodeInput').value='ORI-DUP-001'">填入重复原始编码</button>
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
      <label class="field"><span>验收结果 *</span><select id="acceptanceResult"><option>通过</option><option>待补充资料</option><option>不通过</option></select></label>
      <label class="field"><span>编码及身份核对情况 *</span><input value="编码与实物一致" /></label>
    </div>
    <div class="form-row">
      <label class="field"><span>外观情况 *</span><input value="外观无明显破损" /></label>
      <label class="field"><span>资料完整情况 *</span><input value="资料完整" /></label>
    </div>
    <label class="field"><span>验收说明</span><textarea id="acceptanceNote">人工验收结论记录</textarea></label>
    <div class="toolbar">
      <button onclick="const r=document.querySelector('#acceptanceResult').value; const note=document.querySelector('#acceptanceNote').value.trim(); if(r==='待补充资料'&&!note){showNotice('请填写需要补充资料的说明。','danger');return} if(r==='不通过'&&!note){showNotice('请填写验收不通过原因。','danger');return} if(r==='通过'){state.batteryStatus='已验收待入库'; addEvent('验收通过：状态 待验收 -> 已验收待入库'); showNotice('验收通过，电池进入已验收待入库。','success'); setScreen('inbound-list')} else if(r==='待补充资料'){runSupplement()} else {runRejectedAcceptance()}">保存验收结果</button>
      <button class="secondary" onclick="runRejectedAcceptance()">演示验收不通过</button>
    </div>
  `;
}

function screenSupplement() {
  return `
    <h2>补充资料</h2>
    <label class="field"><span>补充资料说明 *</span><textarea>补充交接证明和照片附件。</textarea></label>
    <label class="field"><span>附件</span><input value="选填：supplement.pdf" /></label>
    <button onclick="state.batteryStatus='待验收'; addEvent('资料补充：状态 待补充资料 -> 待验收'); showNotice('资料已补充，可重新提交验收。','success'); setScreen('acceptance-list')">补充并重新提交</button>
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
      <label class="field"><span>仓库 *</span><select><option>WH-001 主仓库（启用）</option><option>WH-002 停用仓库</option></select></label>
      <label class="field"><span>库位 *</span><select><option>WH-001-A01-R01-L01（启用）</option><option>WH-001-A01-R01-L99（停用）</option></select></label>
    </div>
    <div class="toolbar">
      <button onclick="submitInbound(true)">确认入库</button>
      <button class="secondary" onclick="submitInbound(false)">演示无效仓库或库位</button>
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
