## Why

记账表单目前只接受单个金额。用户在拆分消费或合并小额项目时，需要先在别处计算，再回到表单录入。增加一个轻量四则计算器，可在不改变已有金额输入习惯的情况下完成计算并填入金额。

## What Changes

- 在记账和编辑表单的金额输入框右侧提供计算器入口。
- 弹层提供大按键四则计算、结果预览和填入操作；直接输入普通金额仍沿用现有逻辑。
- 计算值只填入表单金额，不自动保存记录。
- 增加独立表达式计算能力，不改变现有普通金额解析及搜索、CSV、预算、筛选、周期金额行为。

## Capabilities

### New Capabilities
- `amount-calculator`: 在记账表单中计算简单四则表达式并将有效金额填入表单。

### Modified Capabilities

## Impact

- 影响范围：`EditScreen` 金额输入控件及新的计算器弹层/纯运算逻辑；新增金额计算器行为规格。
- 不涉及数据库、CSV、账户余额逻辑；已取消的照片与便携备份需求不属于本提案；不引入数学库。
- 本变更已实现、通过本地自动化与SDK 34原生UI验收，并完成正式发布与CDN分发核验。真机键盘与TalkBack验收尚未完成。

## Constraints and Assumptions

- 普通金额直接输入继续按现有解析规则处理；只在计算器内解释运算表达式。
- 运算结果必须符合现有单笔金额上限 ¥9,999,999.99 且大于零。
- 所有错误状态都用可读文字或高对比边框表达，不依赖颜色。

## Verification

- 本地自动测试58项通过，0 failures、0 errors；SDK 34原生UI通过浅/深色、200%文字与触点位置及计算器保存流程检查。
- Release构建、签名和APK体积核验已完成；详细证据见 `docs/releases/v0.11.1.md`。
- GitHub Release [v0.11.1](https://github.com/Ibis919/ExpenseTracker/releases/tag/v0.11.1) 已发布并为Latest；提交 `fd1dfce` 已合入并推送 main。GitHub及cdn、fastly、gcore三个版本标签APK均为11,101,370字节，SHA256同为 `508491ae729d5b221c9bea3fc1c3788ac4ad6037965ac8fcc5eb2e225b15f579`；三个main清单均为0.11.1并指向gcore版本标签APK；三条purge均完成，throttled=false且CF/FY=true。
