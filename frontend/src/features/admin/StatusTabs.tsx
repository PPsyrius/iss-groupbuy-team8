import { Tabs } from "@heroui/react";

import { LISTING_TABS, type ListingTab } from "./listings-search";

const labels: Record<ListingTab, string> = {
  all: "All",
  draft: "Draft",
  "on-sale": "On sale",
  "off-sale": "Off sale",
};

function isListingTab(key: unknown): key is ListingTab {
  return typeof key === "string" && (LISTING_TABS as readonly string[]).includes(key);
}

// The 4a.2 status tabs: an underline set with a count badge each. Counts are withheld until all
// four are known (4a.4) and stay visible on an empty tab (4a.3). Selecting a tab is a navigation;
// the route owns the URL. The underline is foreground, as drawn, so the accent stays on the one
// primary action; the tabs keep HeroUI's full-width list (the artboard packs them left).
export function StatusTabs({
  tab,
  counts,
  onSelect,
}: {
  tab: ListingTab;
  counts?: Record<ListingTab, number> | undefined;
  onSelect: (tab: ListingTab) => void;
}) {
  return (
    <Tabs
      variant="secondary"
      selectedKey={tab}
      onSelectionChange={(key) => {
        if (isListingTab(key)) onSelect(key);
      }}
    >
      <Tabs.ListContainer>
        <Tabs.List aria-label="Listing status">
          {LISTING_TABS.map((t) => (
            <Tabs.Tab key={t} id={t} className="min-h-11 gap-2 px-4 text-sm">
              {labels[t]}
              {counts === undefined ? null : (
                <span className="bg-default text-muted rounded-full px-2 py-0.5 text-xs tabular-nums">
                  {counts[t]}
                </span>
              )}
              <Tabs.Indicator className="bg-foreground" />
            </Tabs.Tab>
          ))}
        </Tabs.List>
      </Tabs.ListContainer>
    </Tabs>
  );
}
