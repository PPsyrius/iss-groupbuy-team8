import { Button, buttonVariants } from "@heroui/react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useState } from "react";

import { unwrapResult } from "@/api/envelope";
import { pageOrdersOptions } from "@/api/generated/@tanstack/react-query.gen";
import { refundLabel } from "@/features/payment/refund-status";
import { usePaymentAvailability } from "@/features/payment/use-payment-availability";
import { useSession } from "@/features/session/use-session";

export function OrderHistory() {
  const session = useSession();
  const enabled = usePaymentAvailability();
  const [page, setPage] = useState(1);
  const options = pageOrdersOptions({ query: { page, size: 10 } });
  const query = useQuery({
    ...options,
    queryKey: [
      {
        ...options.queryKey[0],
        tags: [session.status === "authenticated" ? session.userId : "anonymous"],
      },
    ],
    enabled,
    select: (r) => unwrapResult(r, { allowVoid: false }),
    retry: false,
  });
  if (session.status !== "authenticated") return null;
  if (!enabled) return <p>Payments and order history are not enabled in this environment.</p>;
  const data = query.data;
  return (
    <section className="flex flex-col gap-4" aria-label="My orders">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold">My orders</h2>
        <Button
          variant="outline"
          isPending={query.isFetching}
          onPress={() => {
            void query.refetch();
          }}
        >
          Refresh orders
        </Button>
      </div>
      {query.isPending ? <output>Loading orders…</output> : null}
      {query.isError ? (
        <p role="alert">Unable to load orders. Check your login and refresh.</p>
      ) : null}
      {data && data.total === 0 ? (
        <div className="border-border bg-surface flex flex-col gap-3 rounded-lg border p-5">
          <h3 className="text-lg font-semibold">You haven’t joined a bloc yet</h3>
          <p>Your paid purchases and refunds will appear here.</p>
          <Link to="/" className={buttonVariants({ variant: "secondary" }) + " self-start"}>
            See what’s filling
          </Link>
        </div>
      ) : null}
      {data?.items?.map((order) => (
        <article
          key={order.id}
          className="border-border bg-surface flex flex-col gap-3 rounded-lg border p-5"
        >
          <h3 className="text-lg font-semibold">{order.productTitle}</h3>
          <p>
            {order.currency?.toUpperCase()} {order.amount} · Quantity {order.quantity}
          </p>
          <dl className="grid grid-cols-[auto_1fr] gap-x-5 gap-y-2 text-sm">
            <dt>Order</dt>
            <dd className="break-all">{order.orderNo}</dd>
            <dt>Purchase status</dt>
            <dd>{order.status}</dd>
            <dt>Bloc status</dt>
            <dd>{order.dealStatus ?? "Awaiting confirmation"}</dd>
            <dt>Paid on</dt>
            <dd>{order.createdAt ? new Date(order.createdAt).toLocaleString() : "—"}</dd>
          </dl>
          {refundLabel(order.refundStatus) ? (
            <output>{refundLabel(order.refundStatus)}</output>
          ) : null}
          {order.paymentId ? (
            <Link
              to="/payments/$paymentId"
              params={{ paymentId: order.paymentId }}
              className="self-start underline"
            >
              Payment and refund details
            </Link>
          ) : null}
        </article>
      ))}
      {data && (data.total ?? 0) > 0 ? (
        <nav aria-label="Order pages" className="flex items-center gap-4">
          <Button
            variant="outline"
            isDisabled={page === 1 || query.isFetching}
            onPress={() => setPage(page - 1)}
          >
            Previous
          </Button>
          <span>
            Page {page} · {data.total} orders
          </span>
          <Button
            variant="outline"
            isDisabled={page * 10 >= (data.total ?? 0) || query.isFetching}
            onPress={() => setPage(page + 1)}
          >
            Next
          </Button>
        </nav>
      ) : null}
    </section>
  );
}
