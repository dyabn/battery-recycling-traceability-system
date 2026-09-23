const baseRules = [
  { code: "DQ-001", name: "系统追溯编码必须唯一", dimension: "唯一性", object: "电池档案", metadata: "全系统系统追溯编码", fields: "battery.system_trace_code", condition: "按 system_trace_code 全系统分组统计", severity: "严重", owner: "业务主管", exemptable: "否", delayed: false },
  { code: "DQ-002", name: "电池核心字段必须完整", dimension: "完整性", object: "电池档案", metadata: "电池包核心字段", fields: "battery_type、battery_chemistry、lifecycle_status、current_responsible_enterprise_id", condition: "核心字段不得为空，允许按数据标准填写“未知”", severity: "一般", owner: "回收操作员", exemptable: "否", delayed: false },
  { code: "DQ-003", name: "原始编码重复必须完成核实", dimension: "一致性", object: "候选登记和重复核实", metadata: "原始编码核实状态", fields: "battery_registration_candidate.candidate_status、duplicate_code_review.review_result", condition: "重复原始编码候选必须关闭并具备核实结果", severity: "严重", owner: "回收操作员", exemptable: "否", delayed: false },
  { code: "DQ-004", name: "未验收通过的电池不能入库", dimension: "合法性", object: "验收记录、入库记录和生命周期事件", metadata: "入库前置状态", fields: "acceptance_result、lifecycle_status、inbound_status、inbound_at、lifecycle_event.occurred_at", condition: "入库记录关联电池必须验收 PASS，且入库前状态可由生命周期事件证明为 ACCEPTED_PENDING_INBOUND", severity: "严重", owner: "业务主管", exemptable: "否", delayed: false },
  { code: "DQ-005", name: "库位必须属于所选仓库", dimension: "一致性", object: "入库记录和库位", metadata: "仓库库位归属", fields: "inbound_record.warehouse_id、inbound_record.location_id、warehouse_location.warehouse_id", condition: "inbound_record.warehouse_id 必须等于 warehouse_location.warehouse_id", severity: "严重", owner: "仓库管理员", exemptable: "否", delayed: false },
  { code: "DQ-006", name: "生命周期事件时间不能倒序", dimension: "时序一致性", object: "生命周期事件", metadata: "关键事件顺序", fields: "lifecycle_event.battery_id、event_type、occurred_at", condition: "REGISTERED <= SUBMITTED_ACCEPTANCE <= ACCEPTANCE_PASSED <= INBOUNDED", severity: "一般", owner: "业务主管", exemptable: "否", delayed: false },
  { code: "DQ-007", name: "当前库存责任企业必须一致", dimension: "一致性", object: "当前库存和电池档案", metadata: "当前责任企业", fields: "inventory.enterprise_id、inventory.is_current、battery.current_responsible_enterprise_id", condition: "inventory.is_current=1 时库存企业必须等于电池当前责任企业", severity: "严重", owner: "仓库管理员", exemptable: "否", delayed: false },
  { code: "DQ-008", name: "已过期临时附件不能绑定", dimension: "有效性", object: "临时附件", metadata: "附件有效期", fields: "business_attachment.binding_status、expires_at", condition: "延期规则，不进入 V1.1", severity: "一般", owner: "系统管理员", exemptable: "否", delayed: true }
];

const issueTemplates = [
  { scope: "battery", rule: "DQ-001", title: "系统追溯编码重复", objectKey: "TRACE-DUP-001", owner: "业务主管", detail: "全系统存在重复 system_trace_code，当前企业仅显示有权查看的对象。", severity: "严重" },
  { scope: "battery", rule: "DQ-002", title: "电池核心字段缺失", objectKey: "BAT-202609-0007", owner: "回收操作员", detail: "battery.current_responsible_enterprise_id 或 battery_chemistry 缺失，必须通过业务更正补齐。", severity: "一般" },
  { scope: "battery", rule: "DQ-003", title: "原始编码重复未核实", objectKey: "OC-7788", owner: "回收操作员", detail: "candidate_status 仍为 PENDING_REVIEW，缺少 duplicate_code_review.review_result。", severity: "严重" },
  { scope: "inventory", rule: "DQ-004", title: "未验收通过电池存在入库记录", objectKey: "INB-202609-002", owner: "业务主管", detail: "acceptance_result 不是 PASS，或 inbound_at 前缺少 ACCEPTED_PENDING_INBOUND 生命周期事件。", severity: "严重" },
  { scope: "inventory", rule: "DQ-005", title: "库位与仓库不一致", objectKey: "INB-202609-003", owner: "仓库管理员", detail: "inbound_record.warehouse_id 与 warehouse_location.warehouse_id 不一致。", severity: "严重" },
  { scope: "trace", rule: "DQ-006", title: "生命周期事件时间倒序", objectKey: "BAT-202609-0003", owner: "业务主管", detail: "lifecycle_event.occurred_at 显示入库事件早于验收通过事件。", severity: "一般" },
  { scope: "inventory", rule: "DQ-007", title: "当前库存责任企业不一致", objectKey: "INV-202609-011", owner: "仓库管理员", detail: "inventory.is_current=1，但 inventory.enterprise_id 与 battery.current_responsible_enterprise_id 不一致。", severity: "严重" }
];

const roleNames = {
  businessSupervisor: "业务主管",
  systemAdmin: "系统管理员",
  recycleOperator: "回收操作员",
  warehouseKeeper: "仓库管理员",
  guest: "无权限用户"
};

let state = {};

function initialRuleStatus() {
  return {
    A: Object.fromEntries(baseRules.map((rule) => [rule.code, !rule.delayed])),
    B: Object.fromEntries(baseRules.map((rule) => [rule.code, !rule.delayed]))
  };
}

function nowText() {
  return new Date().toLocaleString("zh-CN");
}

function resetState() {
  state = {
    runs: [],
    issues: [],
    selectedIssueId: null,
    selectedRule: "DQ-001",
    audit: [],
    issueSequence: 1,
    runSequence: 1,
    ruleStatus: initialRuleStatus(),
    lastRunByEnterprise: { A: null, B: null }
  };
  setNotice("重置完成：规则状态、检查任务和质量问题均已恢复初始状态。", "ok");
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

function ruleActive(rule, enterprise = currentEnterprise()) {
  return !rule.delayed && state.ruleStatus[enterprise][rule.code];
}

function addAudit(action, result, detail, options = {}) {
  const issue = options.issue || null;
  const objectType = options.objectType || (issue ? "data_quality_issue" : "data_quality");
  const objectId = options.objectId || (issue ? issue.id : "-");
  const beforeStatus = options.beforeStatus || "-";
  const afterStatus = options.afterStatus || "-";
  const reason = options.reason || detail;
  state.audit.unshift({
    time: nowText(),
    actor: roleNames[currentRole()],
    enterprise: currentEnterprise(),
    action,
    result,
    objectType,
    objectId,
    beforeStatus,
    afterStatus,
    reason,
    detail,
    issueId: issue ? issue.id : "-"
  });
}

function showView(id) {
  document.querySelectorAll(".view").forEach((view) => view.classList.toggle("active", view.id === id));
  document.querySelectorAll(".nav-item").forEach((item) => item.classList.toggle("active", item.dataset.view === id));
}

function selectedIssue() {
  return state.issues.find((issue) => issue.id === state.selectedIssueId && issue.enterprise === currentEnterprise()) || null;
}

function clearUnauthorizedSelection() {
  if (!selectedIssue()) {
    state.selectedIssueId = null;
  }
}

function candidateTemplates(scope) {
  const scopes = scope === "all" ? ["battery", "inventory", "trace"] : [scope];
  return issueTemplates.filter((template) => scopes.includes(template.scope));
}

function makeIssue(template, runId) {
  return {
    id: `DQI-${String(state.issueSequence++).padStart(3, "0")}`,
    runId,
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
    createdAt: nowText(),
    updatedAt: nowText(),
    audit: ["检查生成 OPEN 问题。"]
  };
}

function createIssuesForRun(scope, runId, allowNewAfterClosed = false) {
  const created = [];
  const matched = [];
  candidateTemplates(scope).forEach((template) => {
    const rule = baseRules.find((item) => item.code === template.rule);
    if (!rule || !ruleActive(rule)) {
      return;
    }
    const sameOpen = state.issues.find((issue) =>
      issue.enterprise === currentEnterprise() &&
      issue.rule === template.rule &&
      issue.objectKey === template.objectKey &&
      issue.status !== "CLOSED"
    );
    if (sameOpen) {
      matched.push(sameOpen);
      return;
    }
    const sameClosed = state.issues.find((issue) =>
      issue.enterprise === currentEnterprise() &&
      issue.rule === template.rule &&
      issue.objectKey === template.objectKey &&
      issue.status === "CLOSED"
    );
    if (sameClosed && !allowNewAfterClosed) {
      matched.push(sameClosed);
      return;
    }
    const issue = makeIssue(template, runId);
    state.issues.push(issue);
    created.push(issue);
  });
  return { created, matched };
}

function runCheck() {
  if (!hasRole("businessSupervisor")) {
    addAudit("发起质量检查", "拒绝", "只有业务主管可以发起检查。", { objectType: "data_quality_check_run", reason: "权限不足" });
    setNotice("无权限：只有业务主管可以发起质量检查。", "danger");
    render();
    return;
  }

  const scope = document.querySelector("#scopeSelect").value;
  const scenario = document.querySelector("#scenarioSelect").value;
  const run = {
    id: `DQR-${String(state.runSequence++).padStart(3, "0")}`,
    scope,
    enterprise: currentEnterprise(),
    status: "RUNNING",
    message: "",
    issueIds: [],
    matchedIssueIds: [],
    checkedRuleCodes: candidateTemplates(scope)
      .map((template) => baseRules.find((rule) => rule.code === template.rule))
      .filter((rule) => rule && ruleActive(rule))
      .map((rule) => rule.code),
    violatedRuleCodes: [],
    startedAt: nowText(),
    completedAt: ""
  };

  if (scenario === "failed") {
    run.status = "FAILED";
    run.message = "检查失败：模拟规则执行异常，未生成质量问题。";
    run.completedAt = nowText();
    state.runs.push(run);
    state.lastRunByEnterprise[currentEnterprise()] = run.id;
    addAudit("质量检查", "失败", run.message, { objectType: "data_quality_check_run", objectId: run.id, beforeStatus: "RUNNING", afterStatus: "FAILED", reason: "模拟执行异常" });
    setNotice(run.message, "danger");
    render();
    showView("result");
    return;
  }

  if (scenario === "clean") {
    run.status = "COMPLETED";
    run.message = "检查完成：未发现质量问题。";
    run.completedAt = nowText();
    state.runs.push(run);
    state.lastRunByEnterprise[currentEnterprise()] = run.id;
    addAudit("质量检查", "通过", run.message, { objectType: "data_quality_check_run", objectId: run.id, beforeStatus: "RUNNING", afterStatus: "COMPLETED", reason: "检查无违规" });
    setNotice(run.message, "ok");
    render();
    showView("result");
    return;
  }

  const { created, matched } = createIssuesForRun(scope, run.id, scenario === "closedAgain");
  run.status = "COMPLETED";
  run.issueIds = created.map((issue) => issue.id);
  run.matchedIssueIds = matched.map((issue) => issue.id);
  run.violatedRuleCodes = [...new Set([...created, ...matched].map((issue) => issue.rule))];
  run.completedAt = nowText();
  run.message = created.length
    ? `检查完成：新增 ${created.length} 个质量问题，命中 ${matched.length} 个已有问题。`
    : "检查完成：未新增问题；相同规则和对象已有未关闭问题或当前规则已停用。";
  state.runs.push(run);
  state.lastRunByEnterprise[currentEnterprise()] = run.id;
  if (created[0]) {
    state.selectedIssueId = created[0].id;
  }
  addAudit("质量检查", "完成", run.message, { objectType: "data_quality_check_run", objectId: run.id, beforeStatus: "RUNNING", afterStatus: "COMPLETED", reason: "手工发起检查" });
  setNotice(run.message, created.length ? "" : "ok");
  render();
  showView("result");
}

function toggleSelectedRule() {
  const rule = baseRules.find((item) => item.code === state.selectedRule);
  const reason = document.querySelector("#ruleReason").value.trim();
  if (!hasRole("systemAdmin")) {
    addAudit("规则启停", "拒绝", "非系统管理员不能启停规则。", { objectType: "data_quality_rule", objectId: state.selectedRule, reason: "权限不足" });
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
  const before = ruleActive(rule) ? "ENABLED" : "DISABLED";
  state.ruleStatus[currentEnterprise()][rule.code] = !state.ruleStatus[currentEnterprise()][rule.code];
  const after = ruleActive(rule) ? "ENABLED" : "DISABLED";
  addAudit("规则启停", "成功", `${rule.code} ${after}。`, { objectType: "data_quality_rule", objectId: rule.code, beforeStatus: before, afterStatus: after, reason });
  setNotice(`${rule.code} 已${after === "ENABLED" ? "启用" : "停用"}，仅影响当前企业。`, "ok");
  render();
}

function editRuleAlgorithm() {
  addAudit("编辑规则算法", "拒绝", "V1.1 不允许在线修改规则算法、字段、判断表达式或脚本。", { objectType: "data_quality_rule", objectId: state.selectedRule, reason: "规则算法冻结" });
  setNotice("固定规则不允许在线编辑算法或脚本。", "danger");
}

function assignIssue() {
  const issue = selectedIssue();
  if (!issue) {
    setNotice("请先选择当前企业可见的质量问题。", "danger");
    return;
  }
  if (!hasRole("businessSupervisor")) {
    addAudit("分配问题", "拒绝", "只有业务主管可以分配问题。", { issue, beforeStatus: issue.status, afterStatus: issue.status, reason: "权限不足" });
    setNotice("无权限：只有业务主管可以分配问题。", "danger");
    return;
  }
  if (issue.status !== "OPEN") {
    setNotice("只有 OPEN 状态的问题可以分配。", "danger");
    return;
  }
  const before = issue.status;
  issue.owner = issue.defaultOwner;
  issue.status = "ASSIGNED";
  issue.updatedAt = nowText();
  issue.audit.push(`业务主管分配给${issue.owner}。`);
  addAudit("分配问题", "成功", `分配给${issue.owner}。`, { issue, beforeStatus: before, afterStatus: issue.status, reason: "分配责任人" });
  setNotice(`${issue.id} 已分配给${issue.owner}。`, "ok");
  render();
  showView("detail");
}

function startProcessing() {
  const issue = selectedIssue();
  if (!issue) {
    setNotice("请先选择当前企业可见的质量问题。", "danger");
    return;
  }
  if (issue.status !== "ASSIGNED" && issue.status !== "REJECTED") {
    setNotice("只有 ASSIGNED 或 REJECTED 状态可以开始处理。", "danger");
    return;
  }
  if (roleNames[currentRole()] !== issue.owner) {
    addAudit("开始处理", "拒绝", "非责任人不能处理。", { issue, beforeStatus: issue.status, afterStatus: issue.status, reason: "非责任人" });
    setNotice("无权限：只有责任人可以开始处理。", "danger");
    return;
  }
  const before = issue.status;
  issue.status = "PROCESSING";
  issue.updatedAt = nowText();
  issue.audit.push(`${issue.owner} 开始处理。`);
  addAudit("开始处理", "成功", "进入 PROCESSING。", { issue, beforeStatus: before, afterStatus: issue.status, reason: "责任人开始处理" });
  setNotice(`${issue.id} 已进入 PROCESSING。`, "ok");
  render();
}

function submitFix() {
  const issue = selectedIssue();
  const note = document.querySelector("#fixNote").value.trim();
  const evidence = document.querySelector("#evidenceInput").value.trim();
  if (!issue) {
    setNotice("请先选择当前企业可见的质量问题。", "danger");
    return;
  }
  if (issue.status !== "PROCESSING") {
    setNotice("只有 PROCESSING 状态可以提交处理。", "danger");
    return;
  }
  if (roleNames[currentRole()] !== issue.owner) {
    addAudit("提交处理", "拒绝", "非责任人不能提交处理。", { issue, beforeStatus: issue.status, afterStatus: issue.status, reason: "非责任人" });
    setNotice("无权限：只有责任人可以提交处理。", "danger");
    return;
  }
  if (!note || !evidence) {
    addAudit("提交处理", "拒绝", "缺少处理说明或更正证据。", { issue, beforeStatus: issue.status, afterStatus: issue.status, reason: "缺少证据" });
    setNotice("请填写处理说明和更正记录或证据。", "danger");
    return;
  }
  const before = issue.status;
  issue.note = note;
  issue.evidence = evidence;
  issue.status = "SUBMITTED";
  issue.updatedAt = nowText();
  issue.audit.push(`提交处理：${note}；证据：${evidence}。`);
  addAudit("提交处理", "成功", "进入 SUBMITTED。", { issue, beforeStatus: before, afterStatus: issue.status, reason: "提交整改证据" });
  setNotice(`${issue.id} 已提交处理。`, "ok");
  render();
}

function startRecheck() {
  const issue = selectedIssue();
  if (!issue) {
    setNotice("请先选择当前企业可见的质量问题。", "danger");
    return;
  }
  if (!hasRole("businessSupervisor")) {
    addAudit("重新检查", "拒绝", "只有业务主管可以重新检查。", { issue, beforeStatus: issue.status, afterStatus: issue.status, reason: "权限不足" });
    setNotice("无权限：只有业务主管可以重新检查。", "danger");
    return;
  }
  if (issue.status !== "SUBMITTED") {
    setNotice("只有 SUBMITTED 状态可以重新检查。", "danger");
    return;
  }
  const before = issue.status;
  issue.status = "RECHECKING";
  issue.updatedAt = nowText();
  issue.audit.push("业务主管发起重新检查，进入 RECHECKING。");
  addAudit("开始重新检查", "成功", "进入 RECHECKING。", { issue, beforeStatus: before, afterStatus: issue.status, reason: "复核整改证据" });
  setNotice(`${issue.id} 已进入 RECHECKING。`, "ok");
  render();
}

function recheck(pass) {
  const issue = selectedIssue();
  if (!issue) {
    setNotice("请先选择当前企业可见的质量问题。", "danger");
    return;
  }
  if (!hasRole("businessSupervisor")) {
    addAudit("重新检查", "拒绝", "只有业务主管可以重新检查。", { issue, beforeStatus: issue.status, afterStatus: issue.status, reason: "权限不足" });
    setNotice("无权限：只有业务主管可以重新检查。", "danger");
    return;
  }
  if (issue.status !== "RECHECKING") {
    setNotice("只有 RECHECKING 状态可以给出重新检查结果。", "danger");
    return;
  }
  const before = issue.status;
  issue.status = pass ? "CLOSED" : "REJECTED";
  issue.updatedAt = nowText();
  issue.audit.push(pass ? "重新检查通过，问题关闭。" : "重新检查不通过，退回继续处理。");
  addAudit("重新检查", pass ? "通过" : "不通过", pass ? "进入 CLOSED。" : "进入 REJECTED。", { issue, beforeStatus: before, afterStatus: issue.status, reason: pass ? "同规则复查通过" : "同规则复查未通过" });
  setNotice(pass ? `${issue.id} 重新检查通过，已关闭。` : `${issue.id} 重新检查失败，已退回。`, pass ? "ok" : "danger");
  render();
}

function editClosedIssue() {
  const issue = selectedIssue();
  if (!issue) {
    setNotice("请先选择当前企业可见的质量问题。", "danger");
    return;
  }
  if (issue.status !== "CLOSED") {
    setNotice("该演示只验证已关闭问题保护，请先关闭问题。", "danger");
    return;
  }
  addAudit("编辑已关闭问题", "拒绝", "已关闭问题不能再次编辑。", { issue, beforeStatus: issue.status, afterStatus: issue.status, reason: "已关闭保护" });
  setNotice("已关闭问题不能再次编辑；再次违规需新检查生成新问题。", "danger");
}

function visibleIssues() {
  const status = document.querySelector("#statusFilter").value;
  const rule = document.querySelector("#ruleFilter").value;
  const owner = document.querySelector("#ownerFilter").value;
  return state.issues.filter((issue) =>
    issue.enterprise === currentEnterprise() &&
    (status === "ALL" || issue.status === status) &&
    (rule === "ALL" || issue.rule === rule) &&
    (owner === "ALL" || issue.owner === owner)
  );
}

function latestRunForEnterprise() {
  const id = state.lastRunByEnterprise[currentEnterprise()];
  return state.runs.find((run) => run.id === id && run.enterprise === currentEnterprise()) || null;
}

function auditForCurrentEnterprise(issue = null) {
  return state.audit.filter((item) =>
    item.enterprise === currentEnterprise() &&
    (!issue || item.issueId === issue.id || item.objectId === issue.id)
  );
}

function renderRules() {
  document.querySelector("#ruleRows").innerHTML = baseRules.map((rule) => `
    <tr>
      <td><input type="radio" name="ruleChoice" value="${rule.code}" ${state.selectedRule === rule.code ? "checked" : ""}></td>
      <td>${rule.code}</td>
      <td>${rule.name}</td>
      <td>${rule.dimension}</td>
      <td>${rule.object}</td>
      <td>${rule.metadata}</td>
      <td>${rule.fields}</td>
      <td>${rule.condition}</td>
      <td>${rule.severity}</td>
      <td>${rule.owner}</td>
      <td>${rule.exemptable}</td>
      <td>${rule.delayed ? "延期" : ruleActive(rule) ? "启用" : "停用"}</td>
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
  select.innerHTML = `<option value="ALL">全部</option>${baseRules.slice(0, 7).map((rule) => `<option value="${rule.code}">${rule.code}</option>`).join("")}`;
  select.value = [...select.options].some((option) => option.value === value) ? value : "ALL";
}

function renderResults() {
  const run = latestRunForEnterprise();
  const runIssueIds = run ? [...run.issueIds, ...run.matchedIssueIds] : [];
  const runIssues = state.issues.filter((issue) => issue.enterprise === currentEnterprise() && runIssueIds.includes(issue.id));
  const passCount = run && run.status === "COMPLETED"
    ? Math.max(run.checkedRuleCodes.length - run.violatedRuleCodes.length, 0)
    : 0;
  document.querySelector("#runStatus").textContent = run ? run.status : "未执行";
  document.querySelector("#issueCount").textContent = run ? String(runIssues.length) : "0";
  document.querySelector("#passCount").textContent = String(passCount);
  document.querySelector("#lastScope").textContent = run ? run.scope : "-";
  document.querySelector("#runMessage").textContent = run ? run.message : "尚未发起检查。";
  document.querySelector("#resultList").innerHTML = run
    ? [`<li>${run.id}：${run.message}</li>`, ...runIssues.map((issue) => `<li>${issue.rule}：${issue.title}（${issue.status}，${issue.objectKey}）</li>`)].join("")
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
  `).join("") : `<tr><td colspan="6" class="muted">没有符合筛选条件的当前企业质量问题。</td></tr>`;
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
    <p><strong>来源检查：</strong>${issue.runId}</p>
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
    <h3>企业审计</h3>
    <ul>${auditForCurrentEnterprise(issue).slice(0, 10).map((item) => `<li>${item.time}｜${item.actor}｜${item.action}｜${item.result}｜${item.objectType}:${item.objectId}｜${item.beforeStatus}->${item.afterStatus}｜${item.reason}</li>`).join("") || "<li>暂无当前企业审计。</li>"}</ul>
  ` : `<p class="muted">尚未选择当前企业可见的质量问题。</p>`;
}

function renderDashboard() {
  const enterpriseIssues = state.issues.filter((issue) => issue.enterprise === currentEnterprise());
  const closed = enterpriseIssues.filter((issue) => issue.status === "CLOSED").length;
  const open = enterpriseIssues.length - closed;
  const rate = enterpriseIssues.length ? Math.round((closed / enterpriseIssues.length) * 100) : 0;
  const completedRuns = state.runs.filter((run) => run.enterprise === currentEnterprise() && run.status === "COMPLETED").length;
  const activeRules = baseRules.filter((rule) => ruleActive(rule)).length;
  document.querySelector("#dashboardRules").textContent = String(activeRules);
  document.querySelector("#dashboardRuns").textContent = String(completedRuns);
  document.querySelector("#dashboardIssues").textContent = String(enterpriseIssues.length);
  document.querySelector("#dashboardClosed").textContent = `${rate}%`;
  document.querySelector("#dashboardOpen").textContent = String(open);
}

function render() {
  clearUnauthorizedSelection();
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

["roleSelect", "statusFilter", "ruleFilter", "ownerFilter"].forEach((id) => {
  document.querySelector(`#${id}`).addEventListener("change", render);
});

document.querySelector("#enterpriseSelect").addEventListener("change", () => {
  clearUnauthorizedSelection();
  setNotice("已切换企业：列表、结果、详情、审计和看板仅显示当前企业数据。", "ok");
  render();
});

document.querySelector("#toggleRuleBtn").addEventListener("click", toggleSelectedRule);
document.querySelector("#editAlgorithmBtn").addEventListener("click", editRuleAlgorithm);
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
