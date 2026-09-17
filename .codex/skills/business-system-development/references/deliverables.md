# 课程交付物规划

课程目标交付物按 27 项规划，但 V0.1 不允许一次性生成 27 个空洞文档。文档只能随生命周期逐步产生。

## 交付物清单

| 编号 | 交付物 | 产生阶段 |
| --- | --- | --- |
| 01 | 业务定位说明 | `business-positioning` |
| 02 | 业务范围与边界 | `business-positioning` |
| 03 | 服务对象与价值承担方 | `business-positioning` |
| 04 | 业务参与者初步清单 | `business-positioning` |
| 05 | 核心业务对象初步清单 | `business-positioning` |
| 06 | 开放问题清单 | `business-positioning` 起，持续维护 |
| 07 | C1 确认清单 | `business-positioning` |
| 08 | 全局业务流程说明 | `global-business-baseline` |
| 09 | 角色职责矩阵 | `global-business-baseline` |
| 10 | 核心业务对象字典 | `global-business-baseline` |
| 11 | 业务状态流转说明 | `global-business-baseline` |
| 12 | 业务规则清单 | `global-business-baseline` |
| 13 | C2 确认清单 | `global-business-baseline` |
| 14 | 首个业务切片说明 | `first-business-slice` |
| 15 | 切片流程说明 | `first-business-slice` |
| 16 | 切片角色与业务对象 | `first-business-slice` |
| 17 | 切片异常场景清单 | `first-business-slice` |
| 18 | 切片验收标准草案 | `first-business-slice` |
| 19 | C3 确认清单 | `first-business-slice` |
| 20 | 系统需求规格说明 | `prototype-and-requirements` |
| 21 | 用户故事清单 | `prototype-and-requirements` |
| 22 | 非功能需求说明 | `prototype-and-requirements` |
| 23 | 测试场景清单 | `prototype-and-requirements` |
| 24 | 原型或 Mock 说明 | `prototype-and-requirements` |
| 25 | C4 确认清单 | `prototype-and-requirements` |
| 26 | 总体设计、数据库和接口设计说明 | `system-design` |
| 27 | 测试、部署与维护说明 | `implementation-and-test` 至 `deployment-and-maintenance` |

## C1 阶段允许输出

当当前阶段为 `business-positioning` 时，只输出 01-07。不要生成 08-27。

## 生成规则

- 每个交付物必须来自已确认信息或明确标记为待确认。
- 缺少业务事实时，写入开放问题。
- 不把文档标题当作完成成果；必须有实际业务内容。
- 不在 C1 阶段生成数据库、接口、页面或代码相关交付物。
