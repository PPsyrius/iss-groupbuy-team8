import { Chip } from "@heroui/react";
import { Link } from "@tanstack/react-router";

import type { OrderSummaryResponse } from "@/api/generated/types.gen";
import { formatDayTime, parseDeadline } from "@/features/catalog/deal-time";
import { formatPrice } from "@/features/catalog/Price";

import { orderStatus } from "./order-status";

// The buyer's orders on `/account` (UC8, #128), in the §4.4 My blocs row shape
// (docs/design-screens.md): 56px thumb, title 14/600, the amount in accent 18/24/600 with the
// order number beside it, a 12px paid line and the status chip. The bloc progress and countdown
// of the artboard row are not drawn: the order schema carries no deal sizes or deadline (they
// belong to T3-03's my-blocs rows). No refund label (option B on #128); the receipt reports it.
// `orders` is the page's `items` as the contract types it (optional); the fallback is one shared
// array so the route passes `items` straight through.
const noOrders: OrderSummaryResponse[] = [];

export function OrderHistory({
  orders = noOrders,
}: {
  orders?: OrderSummaryResponse[] | undefined;
}) {
  return (
    <ul aria-label="My orders" className="flex flex-col gap-3 xl:max-w-[720px]">
      {orders.map((order) => (
        <li key={order.orderId}>
          <OrderRow order={order} />
        </li>
      ))}
    </ul>
  );
}

function OrderRow({ order }: { order: OrderSummaryResponse }) {
  const { productId, productTitle, productImage, quantity = 1, orderNo, currency = "sgd" } = order;
  const status = orderStatus(order.status);
  const paidMs = parseDeadline(order.paidTime);
  const total = Number(order.totalAmount);
  const seats = `${quantity} ${quantity === 1 ? "seat" : "seats"}`;
  return (
    <article className="border-border bg-surface flex gap-3 rounded-lg border p-3.5">
      <div
        aria-hidden="true"
        className="size-14 shrink-0 overflow-hidden rounded-lg bg-[repeating-linear-gradient(135deg,var(--surface-secondary)_0_8px,var(--surface-tertiary)_8px_16px)]"
      >
        {productImage ? (
          <img alt="" className="h-full w-full object-cover" loading="lazy" src={productImage} />
        ) : null}
      </div>
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex items-start justify-between gap-2">
          <h3 className="line-clamp-2 text-sm font-semibold">
            {productId ? (
              <Link to="/products/$productId" params={{ productId }}>
                {productTitle}
              </Link>
            ) : (
              productTitle
            )}
          </h3>
          {status ? (
            <Chip color={status.color} variant="soft" size="sm" className="shrink-0">
              {status.label}
            </Chip>
          ) : null}
        </div>
        <div className="flex flex-wrap items-baseline gap-x-2">
          {Number.isFinite(total) ? (
            <span className="text-accent-soft-foreground text-lg font-semibold tabular-nums">
              {formatPrice(total, currency)}
            </span>
          ) : null}
          <span className="text-muted text-xs">
            {seats}
            {orderNo ? ` · Order ${orderNo}` : ""}
          </span>
        </div>
        {paidMs === null ? null : (
          <span className="text-muted text-xs">Paid · {formatDayTime(paidMs)}</span>
        )}
      </div>
    </article>
  );
}
