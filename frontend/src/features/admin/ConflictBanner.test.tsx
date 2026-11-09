import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { renderWithRouter } from "@/test/render";

import { ConflictBanner, ProblemsCard } from "./ConflictBanner";
import { FormAlert } from "./FormAlert";

// Artboard 4b.4: a page-level warning (not danger: nothing on the server was lost) with one Reload.
describe("ConflictBanner", () => {
  it("renders a title-only form alert without an empty description", async () => {
    await renderWithRouter(<FormAlert alert={{ title: "Save refused", description: "" }} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Save refused");
    expect(screen.queryByRole("link", { name: "Sign in" })).not.toBeInTheDocument();
  });
  it("counts more than ten field problems and explains an unknown server version", () => {
    const fieldProblems = Array.from({ length: 11 }, (_, i) => ({
      label: `Field ${i}`,
      message: "Required",
    }));
    render(<ProblemsCard fieldProblems={fieldProblems} />);
    expect(screen.getByRole("heading", { name: "12 problems to clear" })).toBeVisible();
    expect(
      screen.getByText("Someone else saved a newer version while this page was open."),
    ).toBeVisible();
    expect(screen.getAllByRole("listitem")).toHaveLength(12);
  });
  it("names both versions once the server's is known", () => {
    render(<ConflictBanner loadedVersion={4} serverVersion={5} onReload={() => {}} />);
    expect(screen.getByText("This listing was updated by someone else")).toBeVisible();
    expect(
      screen.getByText(
        "You loaded v4; the server now has v5. Reload to see the latest version — your unsaved changes will be lost.",
      ),
    ).toBeVisible();
  });

  it("says only that the server is newer until its version arrives", () => {
    render(<ConflictBanner loadedVersion={4} onReload={() => {}} />);
    expect(
      screen.getByText(
        "You loaded v4; the server has a newer version. Reload to see the latest version — your unsaved changes will be lost.",
      ),
    ).toBeVisible();
  });

  it("offers Reload as the only way out", async () => {
    const onReload = vi.fn<() => void>();
    render(<ConflictBanner loadedVersion={4} serverVersion={5} onReload={onReload} />);
    await userEvent.click(screen.getByRole("button", { name: "Reload" }));
    expect(onReload).toHaveBeenCalledOnce();
    expect(screen.getAllByRole("button")).toHaveLength(1);
  });

  it("disables Reload while it runs", () => {
    render(<ConflictBanner loadedVersion={4} serverVersion={5} onReload={() => {}} isReloading />);
    expect(screen.getByRole("button", { name: "Reload" })).toBeDisabled();
  });
});
