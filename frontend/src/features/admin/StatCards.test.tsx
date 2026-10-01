import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { StatCards } from "./StatCards";

// A card is the term's group in the description list: label, number, caption.
function card(label: string): HTMLElement {
  const group = screen.getByText(label, { selector: "dt" }).parentElement;
  if (!group) throw new Error(`no card for ${label}`);
  return group;
}

describe("StatCards", () => {
  it("shows the four counts the backend answers, with the 4a.1 captions", () => {
    render(<StatCards counts={{ draft: 3, onSale: 47, offSale: 12, openDeals: 26 }} />);
    expect(screen.getAllByRole("term").map((t) => t.textContent)).toEqual([
      "Drafts",
      "On sale",
      "Off sale",
      "Open blocs",
    ]);
    expect(within(card("Drafts")).getByText("3")).toBeVisible();
    expect(within(card("Drafts")).getByText("private until published")).toBeVisible();
    expect(within(card("On sale")).getByText("47")).toBeVisible();
    expect(within(card("On sale")).getByText("visible on the storefront")).toBeVisible();
    expect(within(card("Off sale")).getByText("12")).toBeVisible();
    expect(within(card("Off sale")).getByText("delisted, no new blocs")).toBeVisible();
    expect(within(card("Open blocs")).getByText("26")).toBeVisible();
    // The artboard's "filling across N listings" has no source in `counts` (admin plan A2-02).
    expect(screen.queryByText(/filling across/)).not.toBeInTheDocument();
  });

  it("reads a count the server leaves out as 0", () => {
    render(<StatCards counts={{ draft: 2 }} />);
    expect(within(card("Open blocs")).getByText("0")).toBeVisible();
  });

  it("withholds the numbers while the counts load", () => {
    const { container } = render(<StatCards counts={undefined} />);
    expect(container.querySelector("dl")).toHaveAttribute("aria-busy", "true");
    expect(screen.getAllByRole("term")).toHaveLength(4);
    expect(container).not.toHaveTextContent(/\d/);
  });

  it("shows a dash, not a skeleton, when the counts read failed", () => {
    const { container } = render(<StatCards counts={undefined} failed />);
    expect(container.querySelector("dl")).not.toHaveAttribute("aria-busy");
    for (const label of ["Drafts", "On sale", "Off sale", "Open blocs"])
      expect(within(card(label)).getByText("—")).toBeVisible();
  });
});
