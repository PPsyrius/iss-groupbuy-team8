import { useState } from "react";

const tile =
  "bg-[repeating-linear-gradient(135deg,var(--surface-secondary)_0_10px,var(--surface-tertiary)_10px_20px)]";

// Failed images reveal the striped tile. Keys reset this state when the source changes.
function GalleryImage({
  src,
  alt,
  loading,
}: Readonly<{
  src: string;
  alt: string;
  loading: "eager" | "lazy";
}>) {
  const [failed, setFailed] = useState(false);
  if (failed) return null;
  return (
    <img
      alt={alt}
      src={src}
      loading={loading}
      className="h-full w-full object-cover"
      onError={() => setFailed(true)}
    />
  );
}

// Position of the selected shot: its own index while that still holds its source, otherwise the
// source's first remaining position (-1 when it was removed).
function selectedShotIndex(
  images: string[],
  selection: { src: string; index: number } | null,
): number {
  if (!selection) return 0;
  if (images[selection.index] === selection.src) return selection.index;
  return images.indexOf(selection.src);
}

// The route keys this component by product ID. Within a product, retain the selected source
// after reordering or choose the first remaining shot if it was removed. Keep the index too
// so duplicate URLs can still be selected individually.
export function ProductGallery({ images }: Readonly<{ images: string[] }>) {
  const [selection, setSelection] = useState<{ src: string; index: number } | null>(() =>
    images[0] ? { src: images[0], index: 0 } : null,
  );
  const selectShot = (src: string, shotIndex: number) => {
    setSelection((previous) =>
      previous?.src === src && previous.index === shotIndex ? previous : { src, index: shotIndex },
    );
  };
  const index = Math.max(0, selectedShotIndex(images, selection));
  const current = images[index];
  const resolvedSelection = current ? { src: current, index } : null;
  // Persist the displayed fallback so later refreshes retain it instead of reviving a removed shot.
  if (selection?.src !== resolvedSelection?.src || selection?.index !== resolvedSelection?.index) {
    setSelection(resolvedSelection);
  }
  const occurrences = new Map<string, number>();
  const shots = images.map((src) => {
    const occurrence = (occurrences.get(src) ?? 0) + 1;
    occurrences.set(src, occurrence);
    // Unlike the array position, a source's occurrence stays stable when other shots move.
    return { src, id: `${occurrence}:${src}` };
  });

  return (
    <section aria-label="Product images" className="flex max-w-full min-w-0 flex-col gap-3">
      <div
        className={
          "relative -mx-4 h-[280px] overflow-hidden xl:mx-0 xl:h-[420px] xl:rounded-lg " + tile
        }
      >
        {current ? (
          <GalleryImage
            key={current}
            src={current}
            alt={`Product shot ${index + 1}`}
            loading="eager"
          />
        ) : null}
        {images.length > 0 ? (
          <span
            aria-live="polite"
            aria-atomic="true"
            className="absolute bottom-3 left-3 rounded-md bg-white px-2 py-1 font-mono text-xs"
          >
            product shot {index + 1} of {images.length}
          </span>
        ) : null}
      </div>
      {images.length > 1 ? (
        <>
          <ul
            aria-label="Choose a product shot"
            className="flex max-w-full gap-1 overflow-x-auto overscroll-x-contain p-1 xl:hidden"
          >
            {shots.map(({ src, id }, i) => (
              <li key={id} className="shrink-0">
                <button
                  type="button"
                  aria-label={`Show product shot ${i + 1}`}
                  aria-pressed={i === index}
                  onClick={() => selectShot(src, i)}
                  className="focus-visible:outline-foreground flex h-11 w-11 items-center justify-center rounded-md focus-visible:outline-2 focus-visible:outline-offset-2"
                >
                  <span
                    aria-hidden="true"
                    className={
                      "block h-2 w-2 rounded-full " + (i === index ? "bg-foreground" : "bg-muted")
                    }
                  />
                </button>
              </li>
            ))}
          </ul>
          <ul
            aria-label="Product thumbnails"
            className="hidden max-w-full gap-3 overflow-x-auto overscroll-x-contain p-1 xl:flex"
          >
            {shots.map(({ src, id }, i) => (
              <li key={id} className="shrink-0">
                <button
                  type="button"
                  aria-label={`Show product shot ${i + 1}`}
                  aria-pressed={i === index}
                  onClick={() => selectShot(src, i)}
                  className={
                    "focus-visible:outline-foreground block h-[84px] w-[112px] overflow-hidden rounded-lg border-2 focus-visible:outline-2 focus-visible:outline-offset-2 " +
                    (i === index ? "border-foreground " : "border-transparent ") +
                    tile
                  }
                >
                  <GalleryImage src={src} alt="" loading="lazy" />
                </button>
              </li>
            ))}
          </ul>
        </>
      ) : null}
    </section>
  );
}
