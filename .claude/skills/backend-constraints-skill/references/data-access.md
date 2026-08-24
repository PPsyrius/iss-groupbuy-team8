# MyBatis、PostgreSQL、Redis 与 Elasticsearch

涉及数据库、缓存、搜索、查询性能或数据迁移时读取。

## 禁止循环查询与批量模式

Mapper/Repository/远程查询不得出现在 `for/while/do`、Stream `map/forEach`、递归、逐项 Future/虚拟线程或循环调用的辅助方法中。正确模式：

```java
Set<Long> productIds = orders.stream()
        .map(Order::productId)
        .collect(Collectors.toSet());
Map<Long, Product> products = productRepository.findByIds(productIds).stream()
        .collect(Collectors.toMap(Product::id, Function.identity()));
orders.forEach(order -> order.attachProduct(products.get(order.productId())));
```

- 空集合直接返回，避免生成 `IN ()`；去重 ID；对超大集合按数据库参数上限和实测性能分块，但分块查询必须在基础设施批量方法内部集中实现并监控，而不是业务层 N 次散查。
- 批量写使用 MyBatis batch/多值 insert 或数据库适合的批处理，控制每批大小与事务内存。
- 禁止 MyBatis `resultMap` 的 nested select 造成隐式 N+1；使用批量二次查询或审慎 join。
- 如果需求天然流式，使用分页/游标分批处理，每批固定次数查询；记录批量大小、总量和失败恢复点。

## MyBatis

- SQL 参数一律 `#{}` 绑定。`${}` 仅允许框架无法绑定的表/列/排序标识符，且值必须来自服务端枚举白名单，绝不接收用户原文。
- 多参数 Mapper 方法显式 `@Param`；复杂条件使用 Query 对象。方法名表达动作和返回规模，如 `selectById`、`selectByIds`、`updateStatusIfVersion`。
- 生产 SQL 明确列名，禁止 `SELECT *`；别名与 `resultMap` 清晰映射 snake_case/camelCase。持久化 DO 不跨基础设施层。
- 动态 SQL 使用 `<where>`、`<set>`、`<foreach>` 等安全标签；不要字符串拼接 SQL。可选条件必须有全表扫描防护。
- 更新/删除必须有明确 WHERE；对期望单行的写操作检查影响行数。0 行区分不存在、版本冲突或状态冲突，映射为对应状态码。
- 分页查询的 count 与 data 条件保持一致；大表深分页优先 keyset。排序包含唯一稳定 tie-breaker。
- 批量 SQL 不绕过领域不变量；只选择必要列，避免大对象和无界结果进入内存。
- 开发/测试可记录慢 SQL 与参数摘要，生产日志需脱敏且不得打印海量参数。

## PostgreSQL

- 表名/列名使用小写 snake_case；主键策略统一。外键、NOT NULL、CHECK、UNIQUE 等能表达的不变量必须下沉数据库，应用校验不是替代品。
- 时间点用 `timestamptz` 并统一 UTC；金额用 `numeric(precision, scale)` 或明确最小单位整数；结构化且需要查询的数据不要随意塞 JSONB。
- 索引由真实查询与选择性驱动；考虑联合索引列顺序、部分索引和覆盖索引。每个索引都有写放大成本，禁止“每列都建索引”。
- 慢查询用 `EXPLAIN (ANALYZE, BUFFERS)` 在近似数据量验证；禁止仅凭 SQL 外观判断。监控顺序扫描、锁等待、膨胀和连接池。
- 默认 Read Committed 不保证两次查询相同快照。涉及抢购库存、成团、支付状态时明确并发模型；优先原子条件更新、唯一约束和版本号，必要时行锁。锁按固定顺序获取以减少死锁。
- 事务短小；禁止长时间 `idle in transaction`。连接池大小与数据库容量协同，绝不把提高连接数当作首选优化。
- 所有 schema 变更由版本化迁移管理；大表 DDL 考虑锁、回填、双写/兼容窗口和回滚。应用先兼容新旧 schema，再做破坏性清理。
- 软删除只在业务确需审计/恢复时使用；所有查询遗漏删除条件的风险必须通过统一策略和测试控制。重要流水优先不可变审计记录。

## Redis

- Redis 是缓存、限流、短期会话或派生状态，不是订单/支付/库存的唯一事实源。
- Key 采用 `blocbuy:{env}:{domain}:{type}:{id}`，明确所有者、TTL、最大基数和清理策略；禁止无 TTL 的临时 key。
- Cache-aside：读 miss 回源，写事务成功后删除/失效缓存；接受并说明短暂不一致。热点 key 用互斥重建/逻辑过期/提前刷新，TTL 加抖动防雪崩；空值短 TTL 防穿透。
- 缓存值带 schema/version 或能安全兼容演进；避免 Java 原生序列化。大 key、热 key、危险全量命令和阻塞 Lua 必须治理。
- 分布式锁必须有唯一 owner token、原子获取和原子校验释放、有限租约与超时；业务仍需数据库约束兜底。不要把 Redis 锁当作绝对正确性证明。
- 限流失败策略按风险选择 fail-open/fail-closed；登录、支付、库存等高风险接口优先安全，并记录指标。

## Elasticsearch

- 使用官方强类型 Java API Client，并与服务端主/次版本兼容策略对齐；禁止采用已弃用 TransportClient/旧 High Level REST Client 新建代码。
- PostgreSQL 为事实源，ES 是可重建读模型。通过 Outbox/CDC/可靠事件异步同步；文档带业务版本，消费端拒绝旧版本覆盖新版本。
- mapping 显式定义 keyword/text/numeric/date；金额、ID、状态不依赖动态映射。需要中文检索时固定分析器和词典版本，变更通过新索引 + alias 原子切换。
- 写入用 Bulk 并限制批量字节/条数/并发；逐项检查部分失败并可重试。不要在循环中逐文档请求。
- 查询限制 `from/size` 和聚合基数；深分页使用 PIT + `search_after` 与稳定排序。用户输入不得直接成为 query_string 脚本或任意字段。
- 搜索结果用于发现候选；下单前回 PostgreSQL 校验当前价格、库存、商品/团状态和权限。
- 记录查询延迟、超时、拒绝、bulk 失败、同步滞后和索引版本；提供全量重建与校验机制。

