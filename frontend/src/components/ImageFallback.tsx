import { useState } from "react";

// The striped tile every image sits on (design screens §4.1 "Card anatomy"; admin plan Global
// Constraints: "Every image has the striped fallback tile"). The image covers the stripes when it
// loads; while it loads, or once it has failed, the stripes show. An empty `alt` does not hide a
// failed image: Chromium still draws the broken-image icon for a sized `<img alt="">`, so the
// `<img>` is removed on `error` and only the tile remains. Decorative: the surrounding text names
// the product. Size it through `className`. A source-keyed child resets failure state when the
// source changes or is cleared, including when an earlier failed URL is restored.
export function ImageFallback({
  src,
  className = "",
}: Readonly<{
  src?: string | null | undefined;
  className?: string;
}>) {
  return (
    <div
      aria-hidden="true"
      data-image-fallback=""
      className={`bg-[repeating-linear-gradient(135deg,var(--surface-secondary)_0_10px,var(--surface-tertiary)_10px_20px)] ${className}`}
    >
      {src ? <FallbackImage key={src} src={src} /> : null}
    </div>
  );
}

function FallbackImage({ src }: Readonly<{ src: string }>) {
  const [failed, setFailed] = useState(false);
  return failed ? null : (
    <img
      alt=""
      className="h-full w-full object-cover"
      loading="lazy"
      onError={() => setFailed(true)}
      src={src}
    />
  );
}
