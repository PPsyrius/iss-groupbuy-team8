import { Chip } from "@heroui/react";
import type { ReactNode } from "react";

import { statusLabel } from "./listing-status";

// The lifecycle as a state trail (artboards 4b.2 and 4b.3): the one-way rule is shown before the
// admin acts. The off-sale trail is not drawn and follows the on-sale one.
const trails: Record<string, readonly string[]> = {
  draft: ["Draft — you are here", "On sale — after publishing", "Off sale — reversible later"],
  on_sale: ["Draft — closed for good", "On sale — you are here", "Off sale — available now"],
  off_sale: ["Draft — closed for good", "On sale — available now", "Off sale — you are here"],
};

const currentIndex: Record<string, number> = { draft: 0, on_sale: 1, off_sale: 2 };

// The editor's sidebar (admin plan A1-06): the Status card with the status and version chips,
// then, on a saved listing, the lifecycle trail. `children` go inside the card (the actions
// available now); the page stacks further cards (the bloc price rule) under it.
export function EditorSidebar({
  status = "draft",
  version,
  showLifecycle,
  children,
}: Readonly<{
  status?: string | undefined;
  version: number;
  showLifecycle: boolean;
  children?: ReactNode;
}>) {
  const trail = trails[status];
  return (
    <section
      aria-labelledby="editor-status"
      className="border-border bg-surface flex flex-col gap-4 rounded-lg border p-5"
    >
      <div className="flex items-center justify-between">
        <h2 id="editor-status" className="font-semibold">
          Status
        </h2>
        <div className="flex items-center gap-2">
          <Chip size="sm" variant="soft" color={status === "on_sale" ? "success" : "default"}>
            {statusLabel(status)}
          </Chip>
          <span className="font-mono text-xs tabular-nums">v{version}</span>
        </div>
      </div>
      {showLifecycle && trail ? (
        <div className="flex flex-col gap-2">
          <span
            id="editor-lifecycle"
            className="text-muted text-xs font-medium tracking-wide uppercase"
          >
            Lifecycle
          </span>
          <ol aria-labelledby="editor-lifecycle" className="flex flex-col gap-1.5">
            {trail.map((step, i) => {
              const here = i === currentIndex[status];
              return (
                <li
                  key={step}
                  aria-current={here ? "step" : undefined}
                  className={`flex items-center gap-2 ${here ? "text-foreground font-medium" : "text-muted"}`}
                >
                  <span
                    aria-hidden="true"
                    className={`size-2 shrink-0 rounded-full ${here ? "bg-foreground" : "bg-border"}`}
                  />
                  {step}
                </li>
              );
            })}
          </ol>
          <p className="text-muted">
            Draft → on sale → off sale → on sale. There is no way back to draft.
          </p>
        </div>
      ) : null}
      {children}
    </section>
  );
}
