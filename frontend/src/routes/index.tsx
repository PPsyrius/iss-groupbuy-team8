import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/")({
  component: () => (
    <main className="p-8">
      <h1 className="text-2xl font-semibold">BlocBuy</h1>
    </main>
  ),
});
