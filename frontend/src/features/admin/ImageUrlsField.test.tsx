import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import { ImageUrlsField } from "./ImageUrlsField";

const noErrors: Record<number, string> = {};

function Harness({
  initial,
  errors = noErrors,
}: {
  initial: string[];
  errors?: Record<number, string>;
}) {
  const [values, setValues] = useState(initial);
  return (
    <ImageUrlsField
      values={values}
      errorAt={(i) => errors[i]}
      onChange={(i, value) => setValues((prev) => prev.map((v, j) => (j === i ? value : v)))}
      onAdd={() => setValues((prev) => [...prev, ""])}
      onRemove={(i) => setValues((prev) => prev.filter((_, j) => j !== i))}
    />
  );
}

const url = "https://cdn.example.com/a.jpg";
const oneBlank = [""];
const twenty = Array<string>(20).fill(url);
const mixed = [url, "http://cdn.example.com/b.jpg", ""];
const secondBad = ["", "ftp://x"];
const secondBadError = { 1: "Use an https:// URL." };

describe("ImageUrlsField", () => {
  it("adds and removes URL rows", async () => {
    const user = userEvent.setup();
    render(<Harness initial={oneBlank} />);
    expect(screen.getByRole("group", { name: /Image URLs/ })).toBeVisible();
    await user.type(screen.getByRole("textbox", { name: "Image URL 1" }), url);
    await user.click(screen.getByRole("button", { name: "Add another URL" }));
    expect(screen.getByRole("textbox", { name: "Image URL 2" })).toHaveValue("");
    await user.click(screen.getByRole("button", { name: "Remove image URL 1" }));
    expect(screen.getAllByRole("textbox")).toHaveLength(1);
    expect(screen.getByRole("textbox", { name: "Image URL 1" })).toHaveValue("");
  });

  it("stops at 20 rows", () => {
    render(<Harness initial={twenty} />);
    expect(screen.getByRole("button", { name: "Add another URL" })).toBeDisabled();
  });

  it("previews only https URLs, over the striped fallback tile", () => {
    const { container } = render(<Harness initial={mixed} />);
    const images = container.querySelectorAll("img");
    expect(images).toHaveLength(1);
    expect(images[0]).toHaveAttribute("src", url);
    expect(container.querySelectorAll("[data-image-fallback]")).toHaveLength(3);
  });

  it("shows a row's error under that row", () => {
    render(<Harness initial={secondBad} errors={secondBadError} />);
    expect(screen.getByRole("textbox", { name: "Image URL 2" })).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.getByText("Use an https:// URL.")).toBeVisible();
  });
});
