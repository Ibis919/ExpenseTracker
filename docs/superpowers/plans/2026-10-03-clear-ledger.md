# 清晰账簿与 F1–F6 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现用户定稿的清晰账簿视觉及 F1–F6，保持简单、色弱友好，签名 APK 不超过 50 MB。

**Architecture:** 继续单 Activity、Compose、Room 和本地存储。功能按顺序实现，数据库通过迁移保留旧记录。使用系统字体与 Android 内置 JSON，不增加网络服务或大型依赖。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Room、Robolectric/JUnit。

## Global Constraints

- 用户辨识红绿黄和蓝紫困难；状态用边框、符号、文字表达，不能只用颜色。
- 默认记一笔、默认微信，保存成功返回并定位账本。
- APK 不超过 50 MB；不额外引入字体包、图库、云账户或预测服务。
- 保留现有分类 emoji 与品牌图标，布局采用 A；所有已有功能保持可用。
- 收入、转账不占消费预算；退款在发生日期冲减净支出，关联原消费且累计退款不能超原金额。
- 合法备份恢复前产生保护快照；损坏/未来格式/失效引用不能覆盖当前数据。
- 发布沿用签名、GitHub Release、仓库 APK 与三个 CDN 更新入口。

## Task 1: F1 保存闭环

Files: `AppViewModel.kt`, `EditScreen.kt`, `HomeScreen.kt`, `ExpenseDb.kt`, `AppViewModelTest.kt`。

Interfaces: `addRecord` 改为 suspend 返回 `Result<Long>`；`updateRecord` suspend 返回 `Result<Unit>`；`savedRecordId: StateFlow<Long?>` 与 `acknowledgeSavedRecord()` 供账本定位。

- [ ] 回归测试：旧月份并带搜索时新增，入库完成后记录可见；数据库触发器拒绝插入时返回失败且没有成功定位。
- [ ] 运行测试，观察旧逻辑不能满足定位断言。
- [ ] 最小实现：`val id = dao.insert(record); _searchQuery.value = ""; _month.value = YearMonth.from(LocalDate.ofEpochDay(epochDay)); _savedRecordId.value = id`；UI 等待结果、保存期间防重入、失败显示并保留输入；成功滚动到记录。
- [ ] 运行该测试集并核对保存调用处。

## Task 2: F2 统计正确性

Files: `StatsScreen.kt`, `AppViewModelTest.kt`, 新 `Statistics.kt`。

Interface: `categoryChanges(current: List<CategoryTotal>, previous: List<CategoryTotal>): List<CategoryChange>`。

- [ ] 测试：上月交通 20000 分、本月缺失，差额必须为 -20000 分。
- [ ] 运行并观察失败，再以两份金额 map 的键并集计算差额，缺项为 0。
- [ ] 标注本月累计/上月整月；使用主题文字和增加/减少符号；图表保留文字摘要。
- [ ] 运行统计回归与构建检查。

## Task 3: F3 完整备份恢复

Files: `BackupManager.kt`, `ExpenseDb.kt`, `AppViewModel.kt`, `SettingsScreen.kt`, 新 `BackupManagerTest.kt`。

Interfaces: 版本化 JSON `BackupSnapshot`；`read/restore/export`；恢复通过 Room transaction 写回全部业务表；预算偏好一并保存。

- [ ] 测试完整往返：活跃与回收站流水、余额基数、预算、分类、周期、模板恢复一致；损坏/未知版本拒绝且当前数据不变；事务失败回滚。
- [ ] 观察缺失能力失败后实现内置 JSON 文件与完整验证。
- [ ] 添加文件导出/恢复入口、备份内容预览和覆盖确认；恢复前保存完整保护快照；每日备份保留七份。
- [ ] 运行备份测试与现有 CSV 回归。

## Task 4: F4 周期/模板账户

Files: `ExpenseDb.kt`, `AppViewModel.kt`, `RecurringScreen.kt`, `EditScreen.kt`, tests。

Interfaces: 周期和模板增加 `paymentMethod: String = ""`；迁移 5→6；新增方法使用可选支付方式参数。

- [ ] 测试周期只生成一次且扣指定账户，旧规则未关联；模板带入对应账户。
- [ ] 观察失败，迁移仅添加默认空字段，新生成记录采用规则账户；旧记录不补写账户。
- [ ] 更新备份序列化及 UI。
- [ ] 运行迁移和周期测试。

## Task 5: F5 结构化筛选

Files: 新 `RecordFilter.kt`、`HomeScreen.kt`、`AppViewModel.kt`、tests。

Interface: `RecordFilter(paymentMethod, excluded, fromDay, toDay, minCents, maxCents)`；与关键词组合；清空全部筛选。

- [ ] 测试账户、代付、日期、金额区间含边界，以及组合条件/合计。
- [ ] 观察失败后实现条件过滤与一个收起的筛选入口；日小计标明净支出和代付。
- [ ] 不创建新页面或新底栏。
- [ ] 运行搜索回归。

## Task 6: F6 收入/转账/退款

Files: 新 `TransactionType.kt`、`ExpenseDb.kt`、`AppViewModel.kt`、`EditScreen.kt`、`HomeScreen.kt`、`StatsScreen.kt`、`TrashScreen.kt`、备份/CSV、tests。

Interfaces: 流水增加 `type: String = "expense"`, `transferTo: String = ""`, `relatedRecordId: Long = 0`；迁移 6→7。账户金额 = 基数 + 各类型有符号变动；预算净支出 = 普通支出 - 普通退款。

- [ ] 测试收入余额增加、转账双账户同步且预算不变、退款关联原消费且不超额、编辑/删除/恢复一致、迁移、完整备份及新版 CSV 往返。
- [ ] 观察失败后统一交易规则，用同一行流水记录转账，避免两个独立余额更新。
- [ ] 在记一笔增加简洁类型选择，退款选原记录、转账选目标账户；旧支出编辑保持原语义。
- [ ] 扩展搜索、统计、回收站、备份与 CSV；旧 CSV 继续导入。
- [ ] 执行全套回归、release 构建与数据审查。

## Task 7: A 视觉与发布

Files: `Theme.kt`、主屏布局、README、版本、update.json、APK。

- [ ] 蓝灰主题、系统字体、等宽数字与层次留白；图表用名称/金额而非颜色识别；保存区可在键盘上方操作。
- [ ] 验证高对比控件、图表文字语义、大字布局、灰度状态；保留所有原入口。
- [ ] 全套测试与 signed release；核对版本、证书、大小 <50 MB、APK SHA256。
- [ ] 提交、合入 main、推送并发布 GitHub Release；刷新 CDN，实际下载比对 APK。

## Progress

- 上述清单保留原实施计划，本段记录实际执行与验收状态。
- [x] F1–F6 与 A 清晰账本布局实现完成；F1 已先观察到旧逻辑定位断言失败，再修复验证。
- [x] 最终全套 53 项测试通过，覆盖周期生成状态、编辑查询保留、退款 CSV 引用与完整备份失败保护。
- [x] SDK 34 原生 Compose 验收保存返回账本、统计、深色模式与 200% 字体关键控件；布局截图已人工查看。
- [x] v0.11.0 / versionCode 22 正式签名构建与 release 必要 lint 通过；APK 11,084,986 字节，旧正式证书保持一致，仓库 APK 与构建包 SHA256 相同。
- [ ] GitHub Release 与 CDN 实际下载校验。
- 真机系统键盘与无障碍朗读尚未验收；原生渲染测试不是手机实测。
