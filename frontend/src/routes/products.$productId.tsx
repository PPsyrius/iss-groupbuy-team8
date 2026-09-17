import { createFileRoute, Link } from "@tanstack/react-router";

// Placeholder until T2-02: registers the `/products/$productId` path so the catalog card's
// `Link` is typed against a real route. T2-02 replaces this with the detail page.
export const Route = createFileRoute("/products/$productId")({
  component: ProductPlaceholder,
});

function ProductPlaceholder() {
  const { productId } = Route.useParams();
  return (
    <main className="mx-auto max-w-6xl p-6">
      <h1 className="text-xl font-semibold">Listing {productId}</h1>
      <Link to="/" className="underline">
        Back to listings
      </Link>
    </main>
  );
}
