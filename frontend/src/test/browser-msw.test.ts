import type { RequestHandler } from "msw";
import { afterEach, expect, it, vi } from "vitest";

import { unwrapResult, type ResultEnvelope } from "@/api/envelope";
import { createClient } from "@/api/generated/client";
import {
  createPaymentCheckout,
  createPaymentIntent,
  getListing,
  getPaymentReceipt,
  pageListings,
} from "@/api/generated/sdk.gen";
import { startBrowserMsw } from "@/test/browser-msw";
import { server } from "@/test/msw";

// Exercise the actual browser handlers through the node transport, without a service worker.
const workerStart = vi.hoisted(() => vi.fn<(options: unknown) => void>());
vi.mock("msw/browser", () => ({
  setupWorker: (...handlers: RequestHandler[]) => ({
    start: (options: unknown) => {
      workerStart(options);
      return Promise.resolve(server.use(...handlers));
    },
  }),
}));

afterEach(() => window.history.replaceState(null, "", "/"));

async function payload<T>(request: Promise<{ data: ResultEnvelope<T> | undefined }>): Promise<T> {
  const response = await request;
  if (!response.data) throw new Error("Missing response envelope");
  const data = unwrapResult(response.data);
  if (!data) throw new Error("Missing response payload");
  return data;
}

it("keeps catalog and detail progress in sync after a mocked paid JOIN", async () => {
  window.history.replaceState(null, "", "/?me=1");
  await startBrowserMsw();
  expect(workerStart).toHaveBeenCalledWith({ onUnhandledFrame: "bypass" });
  const client = createClient({ baseUrl: window.location.origin });
  const before = await payload(pageListings({ client }));
  const listing = before.items?.find(
    (item) =>
      item.deal?.currentSize !== undefined &&
      item.deal.targetSize !== undefined &&
      item.deal.currentSize < item.deal.targetSize,
  );
  const { id, deal } = listing ?? {};
  if (!id || !deal?.dealId || deal.currentSize === undefined) {
    throw new Error("The browser catalog needs a joinable featured bloc");
  }

  const intent = await payload(
    createPaymentIntent({
      client,
      headers: { "X-BlocBuy-Request": crypto.randomUUID() },
      body: { operation: "JOIN", businessId: deal.dealId },
    }),
  );
  if (!intent.intentId) throw new Error("Missing intent");
  const checkout = await payload(
    createPaymentCheckout({
      client,
      headers: {
        "X-BlocBuy-Request": crypto.randomUUID(),
        "Idempotency-Key": crypto.randomUUID(),
      },
      body: { intentId: intent.intentId },
    }),
  );
  const { paymentId } = checkout;
  if (!paymentId) throw new Error("Missing payment");
  const receipt = () => payload(getPaymentReceipt({ client, path: { id: paymentId } }));
  let settled = await receipt();
  await expect
    .poll(
      async () => {
        if (settled.status === "CREATED") settled = await receipt();
        return settled.status;
      },
      { interval: 10, timeout: 1000 },
    )
    .not.toBe("CREATED");
  expect(settled.status).toBe("SUCCEEDED");

  const after = await payload(pageListings({ client }));
  const detail = await payload(getListing({ client, path: { id } }));
  const summary = after.items?.find((item) => item.id === id);
  expect(detail.deal?.currentSize).toBe(deal.currentSize + 1);
  expect(summary?.deal).toEqual(detail.deal);
});
