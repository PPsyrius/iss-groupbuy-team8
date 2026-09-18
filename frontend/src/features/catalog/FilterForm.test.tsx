import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { renderWithRouter } from "@/test/render";

import { FilterForm } from "./FilterForm";

const search = { page: 3, size: 20, category: "household" };

describe("FilterForm", () => {
  it("renders the category chips as links that reset the page, with the current one marked", async () => {
    await renderWithRouter(<FilterForm search={search} />);
    expect(screen.getByRole("link", { name: "Groceries" })).toHaveAttribute(
      "href",
      "/?page=1&size=20&category=groceries",
    );
    expect(screen.getByRole("link", { name: "All" })).toHaveAttribute("href", "/?page=1&size=20");
    const current = screen.getByText("Household");
    expect(current).toHaveAttribute("aria-current", "page");
    expect(current.tagName).not.toBe("A");
    expect(screen.getAllByRole("link")).toHaveLength(5);
  });

  it("keeps the keyword when a chip is chosen", async () => {
    await renderWithRouter(<FilterForm search={{ ...search, keyword: "rice" }} />);
    expect(screen.getByRole("link", { name: "Kids" })).toHaveAttribute(
      "href",
      "/?page=1&size=20&keyword=rice&category=kids",
    );
  });

  it("navigates with the keyword and page 1 on Enter", async () => {
    const user = userEvent.setup();
    const { router } = await renderWithRouter(<FilterForm search={search} />);
    await user.type(screen.getByRole("searchbox", { name: "Search listings" }), "rice{Enter}");
    await waitFor(() =>
      expect(router.state.location.search).toEqual({
        page: 1,
        size: 20,
        category: "household",
        keyword: "rice",
      }),
    );
  });

  it("clears an applied keyword from the field's clear button", async () => {
    const user = userEvent.setup();
    const { router } = await renderWithRouter(
      <FilterForm search={{ ...search, keyword: "rice" }} />,
    );
    expect(screen.getByRole("searchbox", { name: "Search listings" })).toHaveValue("rice");
    await user.click(screen.getByRole("button", { name: /clear/i }));
    await waitFor(() =>
      expect(router.state.location.search).toEqual({ page: 1, size: 20, category: "household" }),
    );
  });

  it("does not navigate on an unchanged or empty submit", async () => {
    const user = userEvent.setup();
    const { router } = await renderWithRouter(<FilterForm search={search} />);
    await user.type(screen.getByRole("searchbox", { name: "Search listings" }), "   {Enter}");
    expect(router.state.location.search).toEqual({});
  });

  it("rejects a keyword over 64 characters without navigating", async () => {
    const user = userEvent.setup();
    const { router } = await renderWithRouter(<FilterForm search={search} />);
    await user.type(
      screen.getByRole("searchbox", { name: "Search listings" }),
      `${"x".repeat(65)}{Enter}`,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("under 64 characters");
    expect(router.state.location.search).toEqual({});
  });
});
