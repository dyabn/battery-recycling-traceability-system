# 追踪规则

追踪目标：确保业务规则、需求、测试、原型、接口、数据库和代码之间能互相定位，便于课程汇报和变更影响分析。

## 编号规范

- `MAT-xxx`：材料来源
- `BIZ-xxx`：业务节点
- `ROLE-xxx`：业务角色
- `OBJ-xxx`：业务对象
- `BR-xxx`：业务规则
- `EX-xxx`：异常场景
- `SLC-xxx`：业务切片
- `UR-xxx`：用户需求
- `FR-xxx`：功能需求
- `NFR-xxx`：非功能需求
- `UC-xxx`：用例
- `AC-xxx`：验收标准
- `TC-xxx`：测试用例
- `UI-xxx`：原型页面或交互
- `API-xxx`：接口契约
- `DB-xxx`：数据表或字段
- `CODE-xxx`：代码模块
- `CHG-xxx`：变更记录
- `Q-xxx`：开放问题

编号不得复用。废弃项保留编号，并标记为 `deprecated`。

## 最小追踪链

每条功能需求至少满足：

`BIZ/BR/EX -> UR/FR -> AC -> TC`

进入工程设计后继续扩展为：

`BIZ/BR/EX -> UR/FR -> AC -> TC -> UI/API/DB -> CODE`

## 来源规则

- 课堂模板、用户输入、业务材料、会议记录和测试结果都应作为来源记录。
- 关键业务规则必须有来源或用户确认。
- 不确定信息进入 `Q-xxx`，不得伪装为已确认规则。

## 变更影响规则

修改以下内容时必须更新追踪记录：

- 业务范围
- 业务流程
- 业务规则
- 状态流转
- 权限边界
- 验收标准
- 测试用例
- 接口契约
- 数据库结构
- 代码模块

每条变更记录应说明是否影响 C1、C2、C3 或 C4 基线。

## 推荐追踪文件

早期可使用 Markdown 表格；需求稳定后可迁移到 CSV、Excel 或测试管理工具。

推荐文件：

- `contracts/traceability-index.md`
- `contracts/change-log.md`
- `contracts/open-questions.md`
