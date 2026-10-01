import { Alert, Button } from "@heroui/react";

// The 412 banner (admin plan A1-08, artboard 4b.4), pinned above the editor. A warning, not an
// error: the server holds a newer, valid version and nothing was lost there. There is no merge
// and no retry (§12 note 7); Reload is the one way out, and it drops the unsaved edits. The
// server's version comes from a background read that leaves the form alone (BA-12: the 412 body
// carries no version), so until it answers the line only says the server is newer.
export function ConflictBanner({
  loadedVersion,
  serverVersion,
  onReload,
  isReloading = false,
  reloadFailed = false,
}: Readonly<{
  loadedVersion: number;
  serverVersion?: number | undefined;
  onReload: () => void;
  isReloading?: boolean;
  reloadFailed?: boolean;
}>) {
  const server =
    serverVersion === undefined
      ? "the server has a newer version"
      : `the server now has v${serverVersion}`;
  return (
    <Alert status="warning" role="alert" className="items-center">
      <Alert.Indicator />
      <Alert.Content>
        <Alert.Title>This listing was updated by someone else</Alert.Title>
        <Alert.Description>
          You loaded v{loadedVersion}; {server}. Reload to see the latest version — your unsaved
          changes will be lost.
        </Alert.Description>
        {reloadFailed ? (
          <Alert.Description className="font-medium">
            We couldn’t reload the listing. Try again.
          </Alert.Description>
        ) : null}
      </Alert.Content>
      <Button variant="tertiary" size="sm" onPress={onReload} isDisabled={isReloading}>
        Reload
      </Button>
    </Alert>
  );
}

const counts = [
  "No",
  "One",
  "Two",
  "Three",
  "Four",
  "Five",
  "Six",
  "Seven",
  "Eight",
  "Nine",
  "Ten",
];

// The 4b.4 side card: the field errors and the conflict arrive together in practice, so they are
// listed together, each fixed its own way. The page shows it only while both kinds are present.
export function ProblemsCard({
  fieldProblems,
  serverVersion,
}: Readonly<{
  fieldProblems: readonly { label: string; message: string }[];
  serverVersion?: number | undefined;
}>) {
  const total = fieldProblems.length + 1;
  const heading = `${counts[total] ?? total} problems to clear`;
  const conflict =
    serverVersion === undefined
      ? "Someone else saved a newer version while this page was open."
      : `Someone else saved v${serverVersion} while this page was open.`;
  return (
    <section
      aria-labelledby="editor-problems"
      className="border-border bg-surface flex flex-col gap-3 rounded-lg border p-5"
    >
      <h2 id="editor-problems" className="font-semibold">
        {heading}
      </h2>
      <ol className="flex list-inside list-decimal flex-col gap-1.5">
        {fieldProblems.map((p) => (
          <li key={p.label}>{`${p.label}: ${p.message}`}</li>
        ))}
        <li>{conflict}</li>
      </ol>
      <p className="text-muted">
        Save stays disabled until both are cleared. The conflict cannot be fixed here — only
        reloading resolves it, and there is no merge.
      </p>
    </section>
  );
}
