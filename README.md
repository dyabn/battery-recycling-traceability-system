# 动力电池回收利用企业流转协同与追溯管理系统

本仓库用于保存软件工程课程项目的全流程成果，包括业务分析、阶段确认、需求追踪、原型、接口、数据库设计、代码、测试和交付材料。

当前状态：已完成项目骨架和 `business-system-development` Skill V0.1 初始化，尚未进入数据库、前端或后端编码阶段。

## 当前阶段

```text
L0 项目材料整理与 Skill V0.1 初始化
```

下一步：

```text
生成《动力电池回收流转与追溯业务定位说明 V0.1》
    ↓
C1 业务定位确认
```

## 项目结构

```text
battery-recycling-traceability-system/
├── .codex/skills/business-system-development/
├── docs/
├── models/
├── contracts/
├── tests/
├── frontend/
├── backend/
├── database/
├── project-state.yaml
├── README.md
└── .gitignore
```

## Skill 说明

`business-system-development` 用于按照课堂模板分阶段推进项目：

```text
项目材料整理
→ 业务定位
→ C1 业务定位确认
→ 建立全局业务基线
→ C2 全局业务基线确认
→ 选择首个垂直业务切片
→ 切片详细分析
→ C3 业务切片确认
→ 推导系统需求
→ 编写验收标准和测试用例
→ UI、API、Mock 原型验证
→ C4 原型确认
→ 数据库、接口和部署设计
→ 系统架构与代码结构设计
→ 编码与自动化测试
→ 回写需求和业务基线
→ 开发下一个业务切片
```

## 阶段约束

- 业务不清楚时，不生成系统功能。
- 业务规则没有依据时，不允许 AI 猜测。
- C1、C2、C3 未确认时，不进入正式系统设计。
- 验收标准和测试场景未建立时，不开始编码。
- 原型没有完成 C4 验证时，不冻结数据库和接口。
- 任何变更必须记录影响范围并更新基线。
- 不允许一次性生成整个系统。

## 校验命令

```powershell
python .codex/skills/business-system-development/scripts/validate-project-state.py
python .codex/skills/business-system-development/scripts/validate-traceability.py
```
