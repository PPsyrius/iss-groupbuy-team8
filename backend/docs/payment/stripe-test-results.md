# Stripe 支付组件测试记录

> Historical test counts. See [release cleanup](stripe-release-cleanup-20260920.md) and the
> sprint log for the post-removal checks. Local HTTP harnesses no longer ship.

> 最新：ZIP 已导入并新增 8 项真实团购／本地订单集成测试，目前专项共 51 项通过。以下保留之前 43 项组件测试记录；以 [本地适配记录](stripe-local-integration-20260920.md) 的范围说明为准。

执行日期：2026-09-20。本地分支：`feature/yorick-stripe-sprint2`，基线：`88a7fb4`。

## 专项测试：43 项通过

运行：`bash scripts/test-stripe-sprint2.sh`。

| 测试集 | 通过 | 失败／错误／跳过 |
| --- | ---: | --- |
| `StripeCheckoutFlowTest` | 27 | 0／0／0 |
| `StripeCheckoutGatewayTest` | 4 | 0／0／0 |
| `StripeWebhookBoundaryTest` | 4 | 0／0／0 |
| `ArchitectureTest` | 8 | 0／0／0 |

27 项流程测试实际执行 PostgreSQL 18.6、现有 V1–V8 和新增显式启用迁移、MyBatis 读写、唯一约束、行锁和事务。覆盖：

1. Checkout 请求重用、付款成功、重复事件、到期退款义务防重。
2. 下游报错后，付款、事件回执和订单同时回滚；重试成功。
3. 供应商金额不一致时进入核查，不履约。
4. Session 已完成但未付款时，不确认付款。
5. 团购已过期后确认付款，保留付款事实并记录补偿。
6. 两笔已付款竞争最后一个名额，仅一笔履约，另一笔待退款。
7. 没有任何 webhook 投递时，人工恢复仍可完成且可重复执行。
8. 已取消付款后出现可信扣款，冻结继续收款，人工恢复后记录补偿。
9. 业务期限结束，显式清理在数据库事务外关闭仍开放的 Session。
10. 同一事件并发投递，只产生一份回执及履约。
11. 非所有者、同一请求键换业务意图被拒绝。
12. 创建网络超时，重试保留同一付款记录、供应商幂等键及参数。
13. 调用方已开启数据库事务时，拒绝在其中执行 Stripe 网络操作。
14. 带中文和换行的签名 HTTP 回调经过控制器、应用门面、支付服务和数据库，重复请求只履约一次。
15. HTTP 回调处理失败返回 500，付款和回执回滚；重新投递后提交成功。
16. 缺失签名或篡改原文返回 400；合法签名的未知事件返回 200，不查询 Stripe 或写业务表。
17. 未付款的开放 Session 保持有负责人的观察单；确认终止且无有效履约后结案，保留审计并解除该异常造成的重试阻止。
18. 人工恢复失败后，处理单仍有负责人及失败原因；没有 webhook 重试也可再执行恢复。
19. 付款表有履约标志但实际下游证据缺失，不关闭处理单。
20. 两名操作员并发恢复同一付款，只保留一份处理单和一次履约。
21. 待退款标志存在但实际退款义务行缺失，不关闭处理单。
22. 只读扫描能发现已取消但仍待核查的付款；不写审计；过去的下次检查时间被拒绝。
23. 创建响应丢失后，用供应商验证的候选 Session 恢复已付款记录，重复执行不创建第二笔 Checkout。
24. 响应丢失但仍开放的 Session 可以补回关联，保持观察，并继续使用原付款页面。
25. 候选 Session 属于其他付款时不关联、不履约，记录失败及查询线索。
26. 人工候选 ID 不能覆盖已有 Session，也不能传入任意 URL 触发请求。
27. 付款编号相同但金额不符的候选 Session 不被绑定，保留核查摘要。

另有 4 项 Spring Boot 完整请求链测试，加载真实过滤器、Sa-Token 拦截器、支付服务和 SDK 验签：匿名合法回调可达，篡改／过期签名、缺失请求头和超限正文被拒绝。该组使用测试会话存储，不连接 Redis；业务端口和支付 Repository 是 mock，断言这些请求不触发业务访问。

HTTP 测试首次发现缺少 `Stripe-Signature` 会被公共异常处理器映射为 500。已新增缺少请求头的 400 映射，复测通过；没有把签名要求设成可选。

Gateway 测试使用 Stripe SDK 验证人工签名、篡改／过期签名、事件版本不匹配，并检查 Checkout 参数。没有真实调用 Stripe 创建或扣款。

专项日志：`/tmp/blocbuy-stripe-session-recovery-final.log`。
最后一次私人测试集群：`/tmp/blocbuy-stripe-test.LToQDF`，已停止；未接触共享数据库。

## 整个后端的本地检查

在 `backend` 下运行：`./mvnw -B -ntp verify -DskipITs`。

检查编译、Surefire 单元测试、Spotless、SpotBugs、Checkstyle 和 ArchUnit。结果为 BUILD SUCCESS，常规测试实际通过 371 项。未提供私人数据库环境变量时，27 项支付数据库测试按保护条件跳过；它们已通过上面的独立脚本另行全部执行。两次运行不能直接相加：专项脚本也执行了常规测试中的 Gateway、请求边界和架构测试。

本命令明确跳过仓库 Failsafe 集成测试。Checkstyle 仍有风格警告，包括部分 record 的格式与规则冲突；构建通过不代表没有警告，也不代表远程 GitHub CI 或 SonarQube 已通过。

构建日志：`/tmp/blocbuy-stripe-session-recovery-verify.log`。

## 这些结果不能证明什么

- Stripe 网络返回由测试替身提供，不是 Stripe Sandbox 的真实扣款证据。
- 团购及订单使用测试协作者和测试表，不能证明与组员实际实现已联调成功。
- 没有验证浏览器登录、HTTP 购买入口、CSRF、限流或云端 TLS 配置。
- 没有执行实际退款；只验证持久化 PENDING 退款义务。
- 已验证处理单的持久化、分派、观察、结案及恢复失败保留；未运行真实协作者、打包后命令、外部告警通知或定时任务。
- GitHub 拉取认证失败；浏览器已只读对比最新 dev，但尚未把最新源码完整同步到本地。

结论：本地支付组件具备可复现的数据库流程测试，适合交给组员核对接口；完整用例仍需真实协作者和 Sandbox 联调。详见同目录 `stripe-sprint2-local.md`。
