const rules = [
  ["DQ-001", "系统追溯编码必须唯一", "唯一性"],
  ["DQ-002", "电池核心字段必须完整", "完整性"],
  ["DQ-003", "原始编码重复必须完成核实", "一致性"],
  ["DQ-004", "未验收通过的电池不能入库", "合法性"],
  ["DQ-005", "库位必须属于所选仓库", "一致性"],
  ["DQ-006", "生命周期事件时间不能倒序", "时序一致性"],
  ["DQ-007", "当前库存责任企业必须一致", "一致性"],
  ["DQ-008", "已过期临时附件不能绑定", "有效性"]
];

const initialIssues = [
  {
    title: "电池核心字段缺失",
    rule: "DQ-002",
    owner: "未分配",
    status: "OPEN",
    detail: "电池包 BAT-202609-0007 缺少电池体系。"
  },
  {
    title: "库位与仓库不一致",
    rule: "DQ-005",
    owner: "未分配",
    status: "OPEN",
    detail: "库存记录引用的库位不属于所选仓库。"
  }
];

let state = {};

function resetState() {
  state = {
    runs: 0,
    checked: false,
    issues: structuredClone(initialIssues)
  };
  render();
}

function showView(id) {
  document.querySelectorAll(".view").forEach((view) => view.classList.toggle("active", view.id === id));
  document.querySelectorAll(".nav-item").forEach((item) => item.classList.toggle("active", item.dataset.view === id));
}

function renderRules() {
  document.querySelector("#ruleRows").innerHTML = rules.map(([code, name, dimension]) => `
    <tr><td>${code}</td><td>${name}</td><td>${dimension}</td><td>待评审候选</td></tr>
  `).join("");
}

function renderResults() {
  document.querySelector("#runStatus").textContent = state.checked ? "COMPLETED" : "未执行";
  document.querySelector("#issueCount").textContent = state.checked ? String(state.issues.length) : "0";
  document.querySelector("#passCount").textContent = state.checked ? String(rules.length - 2) : "0";
  document.querySelector("#resultList").innerHTML = state.checked
    ? state.issues.map((issue) => `<li>${issue.rule}：${issue.title}</li>`).join("")
    : "<li>尚未执行质量检查。</li>";
}

function renderIssues() {
  document.querySelector("#issueRows").innerHTML = state.issues.map((issue) => `
    <tr><td>${issue.title}</td><td>${issue.rule}</td><td>${issue.owner}</td><td>${issue.status}</td></tr>
  `).join("");
}

function renderDetail() {
  const issue = state.issues[0];
  document.querySelector("#issueDetail").innerHTML = `
    <p><strong>问题：</strong>${issue.title}</p>
    <p><strong>规则：</strong>${issue.rule}</p>
    <p><strong>责任人：</strong>${issue.owner}</p>
    <p><strong>状态：</strong>${issue.status}</p>
    <p><strong>描述：</strong>${issue.detail}</p>
  `;
}

function renderDashboard() {
  const closed = state.issues.filter((issue) => issue.status === "CLOSED").length;
  const open = state.issues.length - closed;
  const rate = state.issues.length ? Math.round((closed / state.issues.length) * 100) : 0;
  document.querySelector("#dashboardRuns").textContent = String(state.runs);
  document.querySelector("#dashboardIssues").textContent = String(state.issues.length);
  document.querySelector("#dashboardClosed").textContent = `${rate}%`;
  document.querySelector("#dashboardOpen").textContent = String(open);
}

function render() {
  renderRules();
  renderResults();
  renderIssues();
  renderDetail();
  renderDashboard();
}

document.querySelectorAll(".nav-item").forEach((button) => {
  button.addEventListener("click", () => showView(button.dataset.view));
});

document.querySelector("#runCheckBtn").addEventListener("click", () => {
  state.checked = true;
  state.runs += 1;
  document.querySelector("#runMessage").textContent = "检查完成：发现 2 个待处理质量问题。";
  render();
  showView("result");
});

document.querySelector("#assignBtn").addEventListener("click", () => {
  state.issues.forEach((issue, index) => {
    issue.owner = index === 0 ? "回收操作员" : "仓库管理员";
    issue.status = "ASSIGNED";
  });
  render();
  showView("issues");
});

document.querySelector("#submitFixBtn").addEventListener("click", () => {
  state.issues[0].status = "SUBMITTED";
  render();
});

document.querySelector("#recheckPassBtn").addEventListener("click", () => {
  state.issues[0].status = "CLOSED";
  render();
});

document.querySelector("#recheckFailBtn").addEventListener("click", () => {
  state.issues[0].status = "REJECTED";
  render();
});

document.querySelector("#resetBtn").addEventListener("click", resetState);

resetState();
