import { useState } from "react";

type Trust = { payNow: string; closesMs: number };

// The listing's sign-in gate (`GatedActionSheet`) for an anonymous buyer. `onGated` opens it for
// Start a bloc and Join, `onGatedAlone` for Buy alone; both are undefined once signed in, so the
// actions go straight to checkout. The trust triptych's bloc price and refund promise only hold for
// a bloc, so the Buy alone gate leaves `trust` out (FB-11). Which action opened the gate is kept
// apart from the open flag, so the sheet's content does not change while it animates out.
export function useSignInGate(anonymous: boolean, blocTrust: Trust | undefined) {
  const [isOpen, setOpen] = useState(false);
  const [forBloc, setForBloc] = useState(true);
  const open = (bloc: boolean) => {
    setForBloc(bloc);
    setOpen(true);
  };
  return {
    sheet: { isOpen, onOpenChange: setOpen, trust: forBloc ? blocTrust : undefined },
    onGated: anonymous ? () => open(true) : undefined,
    onGatedAlone: anonymous ? () => open(false) : undefined,
  };
}
