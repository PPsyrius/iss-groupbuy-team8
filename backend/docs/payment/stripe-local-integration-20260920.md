# Stripe 本地适配与验证记录

> Historical Sandbox evidence. The local endpoints and commands below have been retired;
> see [release cleanup](stripe-release-cleanup-20260920.md) for the current boundary.

Update: the teammate's product-page START/JOIN payment connection has since passed local browser acceptance. See [frontend integration evidence and remaining boundaries](stripe-frontend-integration-20260920.md). The record below describes the earlier backend-only acceptance.

2026-09-20。按本轮授权使用本地最小适配，GitHub 没有写入。真实 Stripe Sandbox 验证已完成：三笔测试付款、一次未付款 Session 关闭，以及回调防重、漏收后的人工恢复、到期待退款记录。详细结果见[Sandbox 验收记录](stripe-sandbox-acceptance-20260920.md)。这不是共享部署或完整前端联调验收。

## 代码来源

已导入用户提供的 `iss-groupbuy-team8-dev (1).zip`。ZIP 注释标明提交 `211cfb7d95f60bebdf322ab19a83f43cb70c07ad`，文件 SHA-256 为 `5f926364b3dd342d2bf3a7e1a71cb338c00595d7ab85f911d1ec31d054a7363b`。

相对原基线导入 216 个新增／变化文件，并按 ZIP 的重命名移除旧 `BlocBuyApplicationTests.java`，保留新的 `BlocBuyApplicationTest.java`。没有覆盖已写的支付实现。Git 分支仍叫 `feature/yorick-stripe-sprint2`，HEAD 仍是 `88a7fb4`：这是工作区源码更新，没有伪造 Git 提交历史，也没有 commit、push 或创建 PR。

## 这次接上的部分

| 部分 | 实现与边界 |
| --- | --- |
| 服务端购买意图 | `LocalPaymentBusinessAdapter.createIntent` 从商品、报价策略和本地团购条款表读取价格，不接受请求中的金额。冻结 40 分钟报价；Checkout 为 31 分钟。JOIN 的业务截止时间仍是原团购截止时间，晚到付款需要补偿 |
| 确认付款后开团／参团 | 调用组员现有的 `PaidGroupDealService.startConfirmed/joinConfirmed`，写真实 `biz_group_deal`、`biz_group_member` 和参与回执，没有复制一套假的团购算法 |
| 订单连接 | 本地最小适配创建 `biz_order`，把订单、成员和付款关联，扣减真实本地商品库存。与付款／团购共用事务；失败全部回滚 |
| 团购到期 | `LocalStripeCoordinator.expireTeam` 锁住团购，确认确实到期，再同步调用 `recordExpiredTeam`；到期状态与唯一 `PENDING` 退款义务一起提交 |
| Session 清理 | 每 60 秒检查到期及已关闭团购；`stripe_local_expiry_request` 保存任务、尝试次数和重试时间，进程重启不会丢失。Stripe 请求在数据库事务外执行，失败转人工核查并继续重试 |
| 失败状态 | 仅在供应商确认 Session 已过期且未付款、关联 PaymentIntent 已取消时记为 `FAILED`。开放 Session 上的可重试卡失败仍是 `CREATED`。已确认付款不会降级成失败 |
| 本地操作入口 | `/local/stripe/*` 仅在 `stripe-local-adapter` profile 启用，要求服务器绑定回环地址及私有操作令牌。它是操作者测试入口，可选择虚构买家，不等同于真实用户 Google 登录 |

锁顺序：本地团购锁／现有团购行锁、购买意图、付款、商品库存、团购参与回执和订单。START 尚无团购行时，用事务级 advisory lock 串行化同一预定团购 ID。到期处理先取同一团购锁；不同付款争最后名额仍受组员的团购行锁保护。今后接入其他写入方时，需要共同遵守锁顺序，不能只依赖本地适配的约定。

（2026-09-21 更新）支付分支迁移已并入 `db/migration`（V9–V11）；测试专用订单/意图适配仍在 `src/test/resources/db/stripe-fixture`，仅供测试，永不加入部署 Flyway location。团队完成订单模块后，需替换这个适配并共同核对字段、事务和事件接口。

## 运行方式

仓库根目录：

```bash
python3 scripts/stripe-local-runtime.py start
python3 scripts/stripe-local-smoke.py
python3 scripts/stripe-local-runtime.py status
```

运行目录是 `/Users/spiral/Documents/BlocBuy-Stripe-Local`。脚本生成仅本机使用的随机数据库密码、Redis 密码及操作令牌；目录权限 700，配置文件权限 600。后端执行的是此目录下的 jar 副本，后续 Maven 构建不会覆盖运行中的 jar。

| 服务 | 地址 |
| --- | --- |
| Spring Boot 后端 | `http://127.0.0.1:18589` |
| PostgreSQL 18.6 | `127.0.0.1:55440`，数据库 `blocbuy_stripe_local` |
| Redis 8.10.2 | `127.0.0.1:56379` |
| 健康检查 | `http://127.0.0.1:18589/api/actuator/health` |

目前三个服务已实际启动，健康检查通过。实际 HTTP smoke 已验证操作令牌、拒绝客户端金额、创建数据库购买意图。示例商品和买家均为本地虚构数据。

停止自己的服务并保留数据：

```bash
python3 scripts/stripe-local-runtime.py stop
```

恢复 Stripe 测试配置后，把 `STRIPE_API_KEY=...` 放在仓库外的私有文件，不要发到聊天。然后：

```bash
python3 scripts/stripe-local-runtime.py stop
python3 scripts/stripe-local-runtime.py start --stripe-env /Users/spiral/Documents/BlocBuy-Stripe-Local/stripe.env
python3 scripts/stripe-local-smoke.py --checkout
```

脚本读取测试密钥，启动 Stripe CLI 回调转发，并把当前签名密钥传给后端。只接受 `sk_test_`，拒绝生产密钥。CLI 向本机的转发使用 HTTP；应用到 Stripe 使用 SDK 的 HTTPS，公网部署 TLS 仍需在团队环境另外验收。Stripe 的 CLI 转发方式见[官方文档](https://docs.stripe.com/cli)。

## 验证与交付边界

专项脚本 `bash scripts/test-stripe-sprint2.sh` 已通过 51 项：原有支付流程 27 项、SDK 验签及参数 4 项、HTTP 安全边界 4 项、架构约束 8 项、新本地业务集成 8 项。

新的 8 项覆盖：付款后真实开团与订单、防重；20 人争最后一席，1 人入团、19 笔待退款；到期与晚到付款；订单异常的整体回滚及恢复；HTTP 授权和金额拒绝；持久化 Session 清理；可信终止失败及重新尝试；可重试卡失败不终止付款。真实使用 PostgreSQL、MyBatis、团购代码及本地订单适配，Stripe 网络仍用测试替身。

测试日志：`/tmp/blocbuy-local-adapter-tests.log`。完整构建检查日志：`/tmp/blocbuy-local-adapter-clean-verify.log`。常规构建的 Failsafe Testcontainers 测试未执行，原生 PostgreSQL 专项测试另行执行，不能混算为真实 Stripe 验收。

用户恢复原 Team8 沙盒登录后，已读取测试配置并保存到仓库外私有目录，启动 Stripe CLI 回调转发。托管付款页面、真实 Stripe 事件、服务端 API 查询和本地数据库记录已交叉核对。`scripts/verify-stripe-sandbox.py` 的实际验收结果为 `PASS`；证据在 `evidence/20260920`。原有 51 项专项测试仍覆盖并发、回滚等故障路径，不冒充为 51 次真实 Stripe 交易。

Sprint 2 的退款范围仍是可靠保存 `PENDING` 退款义务。真正调用 Stripe 退款属于 Sprint 3，本轮没有执行退款，也没有改共享 AWS 数据库。
