import { Button } from "@heroui/react";

import { startGoogleLogin } from "./return-to";

// The artboards' single sign-in control (`Button variant="outline" size="lg"`, 20px "G" tile,
// "Continue with Google"): Google is the only provider, so there is no choice architecture. The
// tile is the artboard's placeholder; the official Google mark is CR-16 and must not be redrawn.
export function GoogleSignInButton({
  returnTo,
  label = "Continue with Google",
  className = "",
}: {
  returnTo: string;
  label?: string;
  className?: string;
}) {
  return (
    <Button
      variant="outline"
      size="lg"
      className={"min-h-12 w-full gap-3 text-base font-semibold " + className}
      onPress={() => startGoogleLogin(returnTo)}
    >
      <span
        aria-hidden="true"
        className="bg-surface-tertiary inline-flex size-5 items-center justify-center rounded font-mono text-[10px] text-zinc-600"
      >
        G
      </span>
      {label}
    </Button>
  );
}
