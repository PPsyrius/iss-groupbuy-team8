# REST、异常状态码与 Sa-Token

处理 HTTP 接口、异常、鉴权、权限或外部输入时读取。

## REST API

- 基础路径使用 `/api/v1`；资源使用小写复数名词和必要的层级，例如 `GET /api/v1/orders/{orderId}`、`POST /api/v1/group-deals/{dealId}/participants`。
- HTTP 方法语义：GET 查询且无副作用；POST 创建/非幂等动作；PUT 完整替换且幂等；PATCH 局部变更；DELETE 幂等删除。确属业务动作可用子资源或动作端点，如 `POST /orders/{id}/cancellations`，避免 `/cancelOrder`。
- 创建成功返回 `201 Created` 和 `Location`；异步受理返回 `202 Accepted`；无响应体成功返回 `204 No Content`；校验错误 400；未认证 401；无权限 403；不存在 404；状态冲突/重复 409；并发前置条件失败 412；限流 429。
- 不以 HTTP 200 包装所有失败。错误使用统一 Problem Details 风格结构；成功响应可直接返回 DTO，若项目已有统一成功 envelope 则保持一致但不改变 HTTP 状态语义。
- Path 标识资源，Query 用于筛选、排序和分页。字段、排序键、include/expand 必须白名单化。
- 列表必须分页并设最大页大小；深分页优先稳定排序 + cursor/keyset，响应说明 `nextCursor`。不得提供无界全量查询。
- 时间使用 ISO-8601 并包含时区/偏移；服务端内部用 `Instant` 表示时间点。金额以字符串小数或最小货币单位传输，并明确币种，禁止 JSON 浮点误差。
- DTO 与领域/DO 隔离；请求 DTO 使用 Bean Validation。区分字段格式校验、跨字段校验和领域不变量。
- 写接口定义幂等性。下单、支付、退款、回调等使用 `Idempotency-Key` 或稳定业务请求号，持久化请求指纹与结果；同键不同请求体返回冲突。
- 支持并发修改的资源使用版本号/ETag 或业务版本；不要静默覆盖并发更新。
- OpenAPI 描述路径、状态码、鉴权、分页、幂等、字段约束和示例；契约变更必须向后兼容或升级版本。

## 状态码与业务异常基线

状态码必须稳定、可搜索、可观测，禁止把动态文本当作机器码。推荐最小抽象：

```java
public interface ErrorCode {
    String code();
    HttpStatus httpStatus();
    String defaultMessage();
}

public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND("ORDER_404_001", HttpStatus.NOT_FOUND, "订单不存在"),
    ORDER_STATE_CONFLICT("ORDER_409_001", HttpStatus.CONFLICT, "订单状态不允许当前操作");
    // fields + constructor + interface methods
}

public final class BizException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> details;
    // 只允许 ErrorCode（及安全的结构化 details）构造
}
```

- 每个业务域维护自己的枚举；代码一经对外发布不复用、不改义。文案可本地化，机器码不可本地化。
- `BizException` 只表示可预期业务拒绝；数据库不可用、序列化失败、空指针等是系统异常。
- 如果仓库没有上述等价体系，必须先提示“缺少状态码枚举体系”，推荐优先实现，再新增业务异常。禁止临时 `throw new BizException("库存不足")`。
- 不用异常替代正常分支；批量接口需明确全成全败或逐项结果语义。

## 全局异常处理基线

必须有唯一主入口 `@RestControllerAdvice`，至少处理：

- `BizException`：取枚举的 HTTP 状态与业务码；
- `MethodArgumentNotValidException`、`ConstraintViolationException`、参数解析异常：400，返回安全字段错误；
- Sa-Token 未登录/无权限异常：401/403；
- 资源不存在、冲突、限流等项目异常；
- 未知 `Exception`：记录一次带 traceId 的 ERROR，客户端只得到通用 500。

建议错误体：

```json
{
  "type": "https://blocbuy.example/problems/order-state-conflict",
  "title": "Order state conflict",
  "status": 409,
  "code": "ORDER_409_001",
  "detail": "订单状态不允许当前操作",
  "instance": "/api/v1/orders/123/cancellations",
  "traceId": "...",
  "errors": []
}
```

- `detail/errors` 不包含 SQL、堆栈、内部路径、第三方原始响应或敏感信息。
- 预期 4xx 通常不打印 ERROR 堆栈；未知 5xx 只在边界打印一次。不要 Repository、Service、Controller 多层重复记录同一异常。
- 若没有全局处理器，必须提示“缺少全局异常处理”，推荐优先实现；不得在每个 Controller 临时捕获兜底。

## Sa-Token 统一鉴权

- 使用 Sa-Token 完成登录、注销、会话、角色和权限校验；统一配置 token 名称、有效期、并发登录、续期、Cookie/header 策略和排除路径。
- Controller/路由入口可用 `@SaCheckLogin`、`@SaCheckPermission` 等声明粗粒度权限；应用服务必须继续校验资源归属、商家租户、订单所有者等对象级权限，防止 IDOR。
- 权限标识采用稳定命名，如 `order:read`、`order:cancel`、`merchant:product:write`；拒绝模糊 `admin` 分支散落业务代码。
- 登录态中的用户/租户 ID 由可信上下文获取，禁止相信请求体传入的 ownerId/merchantId。跨租户查询必须强制带租户条件。
- 管理端与用户端明确 token 域、账号类型或登录体系，避免 token 混用；高风险操作实施二次校验和审计。
- Token 只通过 TLS；Cookie 模式配置 Secure、HttpOnly、SameSite 并处理 CSRF；header 模式配置严格 CORS。绝不在日志、URL 或异常体输出 token。
- 登录、短信验证码、密码重置、支付回调、搜索等端点配置分层限流与防重放。验证码一次性、短 TTL、失败次数限制。
- 密码使用经审查的自适应哈希（如 Argon2id/bcrypt），每用户随机盐；禁止可逆加密或通用快速哈希。密钥来自密钥管理/环境注入并支持轮换。
- 所有支付回调先验签，再核对商户号、订单号、金额、币种、时间窗；用唯一约束保证幂等，响应前完成必要的持久化。

