import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { renderWithRouter } from "@/test/render";

import { BlocFilterChips } from "./BlocFilterChips";

const counts = { active: 3, completed: 7, refunded: 2 };

describe("BlocFilterChips", () => {
  it("names the group and marks the selected chip as the current page", async () => {
    await renderWithRouter(<BlocFilterChips selected="active" counts={counts} />);
    expect(screen.getByRole("group", { name: "Show blocs" })).toBeVisible();
    const current = screen.getByText("Active").closest("[aria-current]");
    expect(current).toHaveAttribute("aria-current", "page");
    expect(current).toHaveTextContent("Active 3");
    expect(screen.queryByRole("link", { name: /Active/ })).toBeNull();
    expect(screen.getByRole("link", { name: "Completed 7" })).toBeVisible();
    expect(screen.getByRole("link", { name: "Refunded 2" })).toBeVisible();
  });

  it("links each other chip to /account with its filter in the URL", async () => {
    const { router } = await renderWithRouter(
      <BlocFilterChips selected="active" counts={counts} />,
    );
    await userEvent.click(screen.getByRole("link", { name: "Refunded 2" }));
    await waitFor(() => expect(router.state.location.search).toEqual({ blocs: "refunded" }));
  });
});
