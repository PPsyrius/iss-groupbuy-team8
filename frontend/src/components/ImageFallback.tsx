import { useState } from "react";

// The striped tile every image sits on (design screens §4.1 "Card anatomy"; admin plan Global
// Constraints: "Every image has the striped fallback tile"). The image covers the stripes when it
// loads; while it loads, or once it has failed, the stripes show. An empty `alt` does not hide a
// failed image: Chromium still draws the broken-image icon for a sized `<img alt="">`, so the
// `<img>` is removed on `error` and only the tile remains. Decorative: the surrounding text names
// the product. Size it through `className`.
export function ImageFallback({
  src,
  className = "",
}: Readonly<{
  src?: string | null | undefined;
  className?: string;
}>) {
  // Remember the src that failed instead of a boolean: a new src differs from it, so the image is
  // tried again without an effect that resets state.
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  return (
    <div
      aria-hidden="true"
      data-image-fallback=""
      className={`bg-[repeating-linear-gradient(135deg,var(--surface-secondary)_0_10px,var(--surface-tertiary)_10px_20px)] ${className}`}
    >
      {src && src !== failedSrc ? (
        <img
          alt=""
          className="h-full w-full object-cover"
          loading="lazy"
          onError={() => setFailedSrc(src)}
          src={src}
        />
      ) : null}
    </div>
  );
}
