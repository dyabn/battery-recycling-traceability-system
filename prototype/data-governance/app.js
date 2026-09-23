const rules = [
  { code: "DQ-001", name: "系统追溯编码必须唯一", dimension: "唯一性", object: "电池档案", active: true, delayed: false },
  { code: "DQ-002", name: "电池核心字段必须完整", dimension: "完整性", object: "电池档案", active: true, delayed: false },
  { code: "DQ-003", name: "原始编码重复必须完成核实", dimension: "一致性", object: "候选登记", active: true, delayed: false },
  { code: "DQ-004", name: "未验收通过的电池不能入库", dimension: "合法性", object: "入库记录", active: true, delayed: false },
  { code: "DQ-005", name: "库位必须属于所选仓库", dimension: "一致性", object: "入库记录", active: true, delayed: false },
  { code: "DQ-006", name: "生命周期事件时间不能倒序", dimension: "时序一致性", object: "生命周期事件", active: true, delayed: false },
  { code: "DQ-007", name: "当前库存责任企业必须一致", dimension: "一致性", object: "库存记录", active: true, delayed: false },
  { code: "DQ-008", name: "已过期临时附件不能绑定", dimension: "有效性", object: "临时附件", active: false, delayed: true }
];

const issueTemplates = {
  battery: [
    { rule: "DQ-002", title: "电池核心字段缺失", objectKey: "BAT-202609-0007", owner: "回收操作员", detail: "电池包 BAT-202609-0007 缺少电池体系。", severity: "一般" },
    { rule: "DQ-003", title: "原始编码重复未核实", objectKey: "OC-7788", owner: "回收操作员", detail: "原始编码 OC-7788 存在疑似重复候选，尚未完成核实。", severity: "严重" }
  ],
  inventory: [
    { rule: "DQ-005", title: "库位与仓库不一致", objectKey: "INB-202609-003", owner: "仓库管理员", detail: "入库记录选择 WH-001，但库位属于 WH-002。", severity: "严重" },
    { rule: "DQ-007", title: "库存责任企业不一致", objectKey: "INV-202609-011", owner: "仓库管理员", detail: "库存记录企业与电池当前责任企业不一致。", severity: "严重" }
  ],
  trace: [
    { rule: "DQ-006", title: "生命周期事件时间倒序", objectKey: "BAT-202609-0003", owner: "业务主管", detail: "入库事件时间早于验收通过事件时间。", severity: "一般" }
  ]
};

const roleNames = {
  businessSupervisor: "业务主管",
  systemAdmin: "系统管理员",
  recycleOperator: "回收操作员",
  warehouseKeeper: "仓库管理员",
  guest: "无权限用户"
};

let state = {};

function resetState() {
  state = {
    runs: [],
    issues: [],
    selectedIssueId: null,
    selectedRule: "DQ-001",
    audit: [],
    sequence: 1,
    lastRun: null
  };
  setNotice("重置完成：当前没有质量问题，请先发起检查。", "ok");
  render();
}

function currentRole() {
  return document.querySelector("#roleSelect").value;
}

function currentEnterprise() {
  return document.querySelector("#enterpriseSelect").value;
}

function hasRole(...roles) {
  return roles.includes(currentRole());
}

function setNotice(message, type = "") {
  const notice = document.querySelector("#notice");
  notice.textContent = message;
  notice.className = `notice ${type}`.trim();
}

function addAudit(action, result, detail, issue = null) {
  state.audit.unshift({
    time: new Date().toLocaleString("zh-CN"),
    actor: roleNames[currentRole()],
    enterprise: currentEnterprise(),
    action,
    result,
    detail,
    issueId: issue ? issue.id : "-"
  });
}

function showView(id) {
  document.querySelectorAll(".view").forEach((view) => view.classList.toggle("active", view.id === id));
  document.querySelectorAll(".nav-item").forEach((item) => item.classList.toggle("active", item.dataset.view === id));
}

function selectedIssue() {
  return state.issues.find((issue) => issue.id === state.selectedIssueId) || null;
}

function issueEnterpriseAllowed(issue) {
  return issue && issue.enterprise === currentEnterprise();
}

function makeIssue(template) {
  return {
    id: `DQI-${String(state.sequence++).padStart(3, "0")}`,
    rule: template.rule,
    title: template.title,
    objectKey: template.objectKey,
    defaultOwner: template.owner,
    owner: "未分配",
    status: "OPEN",
    detail: template.detail,
    severity: template.severity,
    enterprise: currentEnterprise(),
    note: "",
    evidence: "",
    audit: []
  };
}

function candidateTemplates(scope) {
  if (scope === "all") {
    return [...issueTemplates.battery, ...issueTemplates.inventory, ...issueTemplates.trace];
  }
  return issueTemplates[scope] || [];
}

function createIssuesForScope(scope, allowNewAfterClosed = false) {
  const created = [];
  candidateTemplates(scope).forEach((template) => {
    const sameOpen = state.issues.find((issue) =>
      issue.enterprise === currentEnterprise() &&
      issue.rule === template.rule &&
      issue.objectKey === template.objectKey &&
      issue.status !== "CLOSED"
    );
    if (sameOpen) {
      return;
    }
    const sameClosed = state.issues.find((issue) =>
      issue.enterprise === currentEnterprise() &&
      issue.rule === template.rule &&
      issue.objectKey === template.objectKey &&
      issue.status === "CLOSED"
    );
    if (sameClosed && !allowNewAfterClosed) {
      return;
    }
    const issue = makeIssue(template);
    issue.audit.push("检查生成 OPEN 问题。");
    state.issues.push(issue);
    created.push(issue);
  });
  return created;
}

function runCheck() {
  if (!hasRole("businessSupervisor")) {
    addAudit("发起质量检查", "拒绝", "只有业务主管可以发起检查。");
    setNotice("无权限：只有业务主管可以发起质量检查。", "danger");
    render();
    return;
  }

  const scope = document.querySelector("#scopeSelect").value;
  const scenario = document.querySelector("#scenarioSelect").value;
  const run = {
    id: `DQR-${String(state.runs.length + 1).padStart(3, "0")}`,
    scope,
    enterprise: currentEnterprise(),
    status: "RUNNING",
    message: ""
  };

  if (scenario === "failed") {
    run.status = "FAILED";
    run.message = "检查失败：模拟规则执行异常，未生成质量问题。";
    state.runs.push(run);
    state.lastRun = run;
    addAudit("质量检查", "失败", run.message);
    setNotice(run.message, "danger");
    render();
    showView("result");
    return;
  }

  if (scenario === "clean") {
    run.status = "COMPLETED";
    run.message = "检查完成：未发现质量问题。";
    state.runs.push(run);
    state.lastRun = run;
    addAudit("质量检查", "通过", run.message);
    setNotice(run.message, "ok");
    render();
    showView("result");
    return;
  }

  const created = createIssuesForScope(scope, scenario === "closedAgain");
  run.status = "COMPLETED";
  run.message = created.length
    ? `检查完成：新增 ${created.length} 个质量问题。`
    : "检查完成：未新增问题；相同规则和对象已有未关闭问题。";
  state.runs.push(run);
  state.lastRun = run;
  if (created[0]) {
    state.selectedIssueId = created[0].id;
  }
  addAudit("质量检查", "完成", run.message);
  setNotice(run.message, created.length ? "" : "ok");
  render();
  showView("result");
}

function toggleSelectedRule() {
  const code = state.selectedRule;
  const rule = rules.find((item) => item.code === code);
  const reason = document.querySelector("#ruleReason").value.trim();
  if (!hasRole("systemAdmin")) {
    addAudit("规则启停", "拒绝", "非系统管理员不能启停规则。");
    setNotice("无权限：只有系统管理员可以启用或停用固定规则。", "danger");
    return;
  }
  if (!rule || rule.delayed) {
    setNotice("延期规则不能在 V1.1 第一版中启用。", "danger");
    return;
  }
  if (!reason) {
    setNotice("请填写规则启停原因。", "danger");
    return;
  }
  rule.active = !rule.active;
  addAudit("规则启停", "成功", `${rule.code} ${rule.active ? "启用" : "停用"}，原因：${reason}`);
  setNotice(`${rule.code} 已${rule.active ? "启用" : "停用"}。`, "ok");
  render();
}

function assignIssue() {
  const issue = selectedIssue();
  if (!issue) {
    setNotice("请先选择一个质量问题。", "danger");
    return;
  }
  if (!issueEnterpriseAllowed(issue)) {
    addAudit("分配问题", "拒绝", "跨企业操作被拒绝。", issue);
    setNotice("企业隔离：不能操作其他企业的问题。", "danger");
    return;
  }
  if (!hasRole("businessSupervisor")) {
    addAudit("分配问题", "拒绝", "只有业务主管可以分配问题。", issue);
    setNotice("无权限：只有业务主管可以分配问题。", "danger");
    return;
  }
  if (issue.status !== "OPEN") {
    setNotice("只有 OPEN 状态的问题可以分配。", "danger");
    return;
  }
  issue.owner = issue.defaultOwner;
  issue.status = "ASSIGNED";
  issue.audit.push(`业务主管分配给${issue.owner}。`);
  addAudit("分配问题", "成功", `分配给${issue.owner}。`, issue);
  setNotice(`${issue.id} 已分配给${issue.owner}。`, "ok");
  render();
  showView("detail");
}

function startProcessing() {
  const issue = selectedIssue();
  if (!issue) {
    setNotice("请先选择一个质量问题。", "danger");
    return;
  }
  if (!issueEnterpriseAllowed(issue)) {
    addAudit("开始处理", "拒绝", "跨企业操作被拒绝。", issue);
    setNotice("企业隔离：不能操作其他企业的问题。", "danger");
    return;
  }
  if (issue.status !== "ASSIGNED" && issue.status !== "REJECTED") {
    setNotice("只有 ASSIGNED 或 REJECTED 状态可以开始处理。", "danger");
    return;
  }
  if (roleNames[currentRole()] !== issue.owner) {
    addAudit("开始处理", "拒绝", "非责任人不能处理。", issue);
    setNotice("无权限：只有责任人可以开始处理。", "danger");
    return;
  }
  issue.status = "PROCESSING";
  issue.audit.push(`${issue.owner} 开始处理。`);
  addAudit("开始处理", "成功", "进入 PROCESSING。", issue);
  setNotice(`${issue.id} 已进入 PROCESSING。`, "ok");
  render();
}

function submitFix() {
  const issue = selectedIssue();
  const note = document.querySelector("#fixNote").value.trim();
  const evidence = document.querySelector("#evidenceInput").value.trim();
  if (!issue) {
    setNotice("请先选择一个质量问题。", "danger");
    return;
  }
  if (!issueEnterpriseAllowed(issue)) {
    addAudit("提交处理", "拒绝", "跨企业操作被拒绝。", issue);
    setNotice("企业隔离：不能操作其他企业的问题。", "danger");
    return;
  }
  if (issue.status !== "PROCESSING") {
    setNotice("只有 PROCESSING 状态可以提交处理。", "danger");
    return;
  }
  if (roleNames[currentRole()] !== issue.owner) {
    addAudit("提交处理", "拒绝", "非责任人不能提交处理。", issue);
    setNotice("无权限：只有责任人可以提交处理。", "danger");
    return;
  }
  if (!note || !evidence) {
    setNotice("请填写处理说明和更正记录或证据。", "danger");
    return;
  }
  issue.note = note;
  issue.evidence = evidence;
  issue.status = "SUBMITTED";
  issue.audit.push(`提交处理：${note}；证据：${evidence}。`);
  addAudit("提交处理", "成功", "进入 SUBMITTED。", issue);
  setNotice(`${issue.id} 已提交处理。`, "ok");
  render();
}

function startRecheck() {
  const issue = selectedIssue();
  if (!issue) {
    setNotice("请先选择一个质量问题。", "danger");
    return;
  }
  if (!issueEnterpriseAllowed(issue)) {
    addAudit("重新检查", "拒绝", "跨企业操作被拒绝。", issue);
    setNotice("企业隔离：不能操作其他企业的问题。", "danger");
    return;
  }
  if (!hasRole("businessSupervisor")) {
    addAudit("重新检查", "拒绝", "只有业务主管可以重新检查。", issue);
    setNotice("无权限：只有业务主管可以重新检查。", "danger");
    return;
  }
  if (issue.status !== "SUBMITTED") {
    setNotice("只有 SUBMITTED 状态可以重新检查。", "danger");
    return;
  }
  issue.status = "RECHECKING";
  issue.audit.push("业务主管发起重新检查，进入 RECHECKING。");
  addAudit("开始重新检查", "成功", "进入 RECHECKING。", issue);
  setNotice(`${issue.id} 已进入 RECHECKING。`, "ok");
  render();
}

function recheck(pass) {
  const issue = selectedIssue();
  if (!issue) {
    setNotice("请先选择一个质量问题。", "danger");
    return;
  }
  if (!issueEnterpriseAllowed(issue)) {
    addAudit("重新检查", "拒绝", "跨企业操作被拒绝。", issue);
    setNotice("企业隔离：不能操作其他企业的问题。", "danger");
    return;
  }
  if (!hasRole("businessSupervisor")) {
    addAudit("重新检查", "拒绝", "只有业务主管可以重新检查。", issue);
    setNotice("无权限：只有业务主管可以重新检查。", "danger");
    return;
  }
  if (issue.status !== "RECHECKING") {
    setNotice("只有 RECHECKING 状态可以给出重新检查结果。", "danger");
    return;
  }
  if (pass) {
    issue.status = "CLOSED";
    issue.audit.push("重新检查通过，问题关闭。");
    addAudit("重新检查", "通过", "进入 CLOSED。", issue);
    setNotice(`${issue.id} 重新检查通过，已关闭。`, "ok");
  } else {
    issue.status = "REJECTED";
    issue.audit.push("重新检查不通过，退回继续处理。");
    addAudit("重新检查", "不通过", "进入 REJECTED。", issue);
    setNotice(`${issue.id} 重新检查失败，已退回。`, "danger");
  }
  render();
}

function editClosedIssue() {
  const issue = selectedIssue();
  if (!issue) {
    setNotice("请先选择一个质量问题。", "danger");
    return;
  }
  if (issue.status !== "CLOSED") {
    setNotice("该演示只验证已关闭问题保护，请先关闭问题。", "danger");
    return;
  }
  addAudit("编辑已关闭问题", "拒绝", "已关闭问题不能再次编辑。", issue);
  setNotice("已关闭问题不能再次编辑；再次违规需新检查生成新问题。", "danger");
}

function visibleIssues() {
  const status = document.querySelector("#statusFilter").value;
  const rule = document.querySelector("#ruleFilter").value;
  const owner = document.querySelector("#ownerFilter").value;
  return state.issues.filter((issue) =>
    (status === "ALL" || issue.status === status) &&
    (rule === "ALL" || issue.rule === rule) &&
    (owner === "ALL" || issue.owner === owner)
  );
}

function renderRules() {
  document.querySelector("#ruleRows").innerHTML = rules.map((rule) => `
    <tr>
      <td><input type="radio" name="ruleChoice" value="${rule.code}" ${state.selectedRule === rule.code ? "checked" : ""}></td>
      <td>${rule.code}</td>
      <td>${rule.name}</td>
      <td>${rule.dimension}</td>
      <td>${rule.object}</td>
      <td>${rule.delayed ? "延期" : rule.active ? "启用" : "停用"}</td>
    </tr>
  `).join("");
  document.querySelectorAll("input[name='ruleChoice']").forEach((input) => {
    input.addEventListener("change", () => {
      state.selectedRule = input.value;
    });
  });
}

function renderRuleFilter() {
  const select = document.querySelector("#ruleFilter");
  const value = select.value;
  select.innerHTML = `<option value="ALL">全部</option>${rules.slice(0, 7).map((rule) => `<option value="${rule.code}">${rule.code}</option>`).join("")}`;
  select.value = [...select.options].some((option) => option.value === value) ? value : "ALL";
}

function renderResults() {
  const run = state.lastRun;
  const activeRules = rules.filter((rule) => rule.active && !rule.delayed).length;
  document.querySelector("#runStatus").textContent = run ? run.status : "未执行";
  document.querySelector("#issueCount").textContent = String(state.issues.filter((issue) => issue.enterprise === currentEnterprise()).length);
  document.querySelector("#passCount").textContent = run && run.status === "COMPLETED" ? String(Math.max(activeRules - state.issues.length, 0)) : "0";
  document.querySelector("#lastScope").textContent = run ? run.scope : "-";
  document.querySelector("#runMessage").textContent = run ? run.message : "尚未发起检查。";
  document.querySelector("#resultList").innerHTML = run
    ? [`<li>${run.id}：${run.message}</li>`, ...state.issues.filter((issue) => issue.enterprise === currentEnterprise()).map((issue) => `<li>${issue.rule}：${issue.title}（${issue.status}）</li>`)].join("")
    : "<li>尚未执行质量检查。</li>";
}

function renderIssues() {
  const rows = visibleIssues();
  document.querySelector("#issueRows").innerHTML = rows.length ? rows.map((issue) => `
    <tr>
      <td><input type="radio" name="issueChoice" value="${issue.id}" ${state.selectedIssueId === issue.id ? "checked" : ""}></td>
      <td>${issue.title}</td>
      <td>${issue.rule}</td>
      <td>${issue.owner}</td>
      <td><span class="status ${issue.status === "CLOSED" ? "closed" : issue.status === "REJECTED" ? "rejected" : ""}">${issue.status}</span></td>
      <td>${issue.enterprise}</td>
    </tr>
  `).join("") : `<tr><td colspan="6" class="muted">没有符合筛选条件的质量问题。</td></tr>`;
  document.querySelectorAll("input[name='issueChoice']").forEach((input) => {
    input.addEventListener("change", () => {
      state.selectedIssueId = input.value;
      renderDetail();
    });
  });
}

function renderDetail() {
  const issue = selectedIssue();
  document.querySelector("#issueDetail").innerHTML = issue ? `
    <p><strong>问题：</strong>${issue.title}</p>
    <p><strong>对象：</strong>${issue.objectKey}</p>
    <p><strong>规则：</strong>${issue.rule}</p>
    <p><strong>严重程度：</strong>${issue.severity}</p>
    <p><strong>责任人：</strong>${issue.owner}</p>
    <p><strong>状态：</strong>${issue.status}</p>
    <p><strong>企业：</strong>${issue.enterprise}</p>
    <p><strong>描述：</strong>${issue.detail}</p>
    <p><strong>处理说明：</strong>${issue.note || "未提交"}</p>
    <p><strong>更正证据：</strong>${issue.evidence || "未提交"}</p>
    <h3>问题审计</h3>
    <ul>${issue.audit.map((item) => `<li>${item}</li>`).join("") || "<li>暂无问题审计。</li>"}</ul>
    <h3>全局审计</h3>
    <ul>${state.audit.filter((item) => item.issueId === issue.id || item.issueId === "-").slice(0, 8).map((item) => `<li>${item.time}｜${item.actor}｜${item.action}｜${item.result}｜${item.detail}</li>`).join("") || "<li>暂无全局审计。</li>"}</ul>
  ` : `<p class="muted">尚未选择质量问题。</p>`;
}

function renderDashboard() {
  const enterpriseIssues = state.issues.filter((issue) => issue.enterprise === currentEnterprise());
  const closed = enterpriseIssues.filter((issue) => issue.status === "CLOSED").length;
  const open = enterpriseIssues.length - closed;
  const rate = enterpriseIssues.length ? Math.round((closed / enterpriseIssues.length) * 100) : 0;
  const completedRuns = state.runs.filter((run) => run.enterprise === currentEnterprise() && run.status === "COMPLETED").length;
  const activeRules = rules.filter((rule) => rule.active && !rule.delayed).length;
  document.querySelector("#dashboardRules").textContent = String(activeRules);
  document.querySelector("#dashboardRuns").textContent = String(completedRuns);
  document.querySelector("#dashboardIssues").textContent = String(enterpriseIssues.length);
  document.querySelector("#dashboardClosed").textContent = `${rate}%`;
  document.querySelector("#dashboardOpen").textContent = String(open);
}

function render() {
  renderRules();
  renderRuleFilter();
  renderResults();
  renderIssues();
  renderDetail();
  renderDashboard();
}

document.querySelectorAll(".nav-item").forEach((button) => {
  button.addEventListener("click", () => showView(button.dataset.view));
});

["roleSelect", "enterpriseSelect", "statusFilter", "ruleFilter", "ownerFilter"].forEach((id) => {
  document.querySelector(`#${id}`).addEventListener("change", render);
});

document.querySelector("#toggleRuleBtn").addEventListener("click", toggleSelectedRule);
document.querySelector("#runCheckBtn").addEventListener("click", runCheck);
document.querySelector("#assignBtn").addEventListener("click", assignIssue);
document.querySelector("#startProcessBtn").addEventListener("click", startProcessing);
document.querySelector("#submitFixBtn").addEventListener("click", submitFix);
document.querySelector("#startRecheckBtn").addEventListener("click", startRecheck);
document.querySelector("#recheckPassBtn").addEventListener("click", () => recheck(true));
document.querySelector("#recheckFailBtn").addEventListener("click", () => recheck(false));
document.querySelector("#editClosedBtn").addEventListener("click", editClosedIssue);
document.querySelector("#resetBtn").addEventListener("click", resetState);

resetState();
