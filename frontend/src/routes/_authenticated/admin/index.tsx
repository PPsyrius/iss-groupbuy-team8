import { createFileRoute } from "@tanstack/react-router";

// Placeholder until the dashboard lands (A2-02).
export const Route = createFileRoute("/_authenticated/admin/")({
  component: AdminDashboard,
});

function AdminDashboard() {
  return (
    <div className="flex flex-col gap-1">
      <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
      <p className="text-muted">Listings and bloc price rules will be managed here.</p>
    </div>
  );
}
