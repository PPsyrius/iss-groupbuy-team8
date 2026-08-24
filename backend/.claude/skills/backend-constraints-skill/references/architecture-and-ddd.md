# 模块化单体、DDD 与 COLA

仅在新增模块、移动代码、设计领域模型或审查依赖边界时读取。

## 标准结构

BlocBuy 是单进程、单部署单元的模块化单体。建议 Maven 聚合结构如下；`client` 与 `bootstrap` 是契约/装配模块，不计入四个业务层：

```text
blocbuy-parent/
├── blocbuy-client/          # 对外 DTO、API/事件契约；不放业务实现
├── blocbuy-adapter/         # Web、RPC、MQ consumer、scheduler 等入站适配
├── blocbuy-application/     # command/query、用例服务、事务编排、assembler
├── blocbuy-domain/          # 聚合、实体、值对象、领域服务/事件、端口
├── blocbuy-infrastructure/  # MyBatis、PG、Redis、ES、MQ producer、第三方实现
└── blocbuy-bootstrap/       # Spring Boot 启动、配置与最终装配
```

每个模块必须先按业务域分包，禁止全局 `controller/service/mapper/entity` 大杂烩：

```text
com.blocbuy
├── identity
├── catalog
├── groupbuy
├── order
├── inventory
├── payment
├── merchant
├── marketing
├── fulfillment
├── search
└── notification
```

示例：`blocbuy-domain/.../groupbuy/model/aggregate/GroupDeal.java`、`blocbuy-application/.../groupbuy/command/CreateGroupDealCommand.java`。具体业务域由领域建模决定，不因列表存在就提前建空模块。

## 依赖方向

允许的核心依赖：

```text
adapter -> application -> domain
infrastructure ---------> domain
bootstrap -> adapter + application + infrastructure
client <- adapter/application（仅契约）
domain -> JDK + 极少量无框架通用类型
```

- 外层依赖内层；领域层不能反向依赖外层。
- 应用层不得直接依赖 MyBatis Mapper、RedisTemplate、ElasticsearchClient 或 RabbitTemplate，只依赖领域端口/应用端口。
- 基础设施层实现端口并完成 DO/PO 与领域对象转换；不得把持久化注解和表结构泄漏进聚合。
- 跨域调用使用对方公开的应用接口、查询门面或领域事件；禁止跨域 join/Mapper 调用来复用“方便的数据”。读模型确需跨域汇总时，建立显式查询模型并记录所有权。
- 用 Maven 模块依赖和 ArchUnit 同时守住边界；禁止靠口头约定。

## 四层职责

### Adapter

- Controller 将 HTTP 请求转为 Command/Query；Consumer 将消息转为应用命令。
- 做协议级校验、Sa-Token 鉴权入口、header/path/query 解析和 DTO 转换。
- 不开启业务事务，不写核心业务规则，不调用 Mapper，不拼 SQL，不直接操作缓存。

### Application

- 每个公开方法对应清晰用例；命令与查询可分离建模，不强制引入复杂 CQRS 框架。
- 编排聚合、领域服务、仓储、事件和外部端口；事务边界通常放在命令用例服务公开方法。
- 不承载可归属于单个聚合的规则；不返回持久化 DO。

### Domain

- 聚合根是修改聚合内部状态的唯一入口；构造/工厂方法确保对象从出生起有效。
- 实体以稳定身份判等；值对象按值判等且尽量不可变；金额用 `BigDecimal`/Money 值对象，禁止 `double`。
- 规则放入聚合；跨实体且无自然归属的规则放领域服务；外部依赖用 Gateway/Repository 端口。
- 领域事件使用已经发生的过去式语义，例如 `OrderCreated`；事件只携带稳定、最小的业务事实。
- 聚合边界以一致性约束决定，不把完整订单、商品、商家、会员对象塞进一个巨型聚合；跨聚合只持有 ID/值对象。

### Infrastructure

- 实现仓储、查询、缓存、搜索、消息发布、第三方客户端和技术配置。
- `Mapper + DO/PO + RepositoryImpl + Converter` 同属基础设施；Mapper 不越层暴露。
- 处理超时、重试、熔断、协议错误转换和资源关闭；把技术异常转换为项目定义的系统异常，不伪装成业务失败。

## 领域建模约束

- 先定义术语和不变量，再定义表和接口；一个概念在同一业务域内只使用一个名称。
- 命令表达意图（`ConfirmOrderCommand`），查询表达所需视图（`OrderDetailQuery`），禁止万能 `save/update/process`。
- 状态迁移通过行为方法完成并校验当前状态；Controller/Application 不允许直接 `setStatus`。
- 领域对象避免公共 setter；为必要变化提供语义化方法。不要使用 Lombok `@Data` 破坏封装或让聚合生成错误的 `equals/hashCode/toString`。
- Repository 按聚合根设计，不为每张表机械创建仓储。查询读模型可以使用专用 QueryGateway。
- `common` 只放真正稳定、无业务归属的最小共享内核；禁止把不知放哪里的代码塞进 `common/utils`。
- 域间一致性优先使用本地事务内的清晰应用调用；只有解耦、耗时或最终一致性确有价值时才使用事件，避免“为了事件而事件”。

## BlocBuy 关键边界

- 下单：价格快照、促销结果、收货信息快照必须可追溯，不能只引用会变化的当前值。
- 库存：扣减必须由数据库条件更新/版本约束等原子机制保护，禁止“先查库存再无条件更新”。
- 支付/退款：以支付渠道流水号和业务单号建立唯一约束；回调必须验签、核对金额/币种/商户并幂等处理。
- 团购成团：定义人数计算、占位、超时、取消、退款的单一状态机；重复事件不得导致重复成团或退款。
- 搜索与推荐是派生视图；价格、库存、可售状态在关键交易步骤回源事实源确认。

