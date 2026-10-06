import { Alert } from "@heroui/react";
import { Link, useLocation } from "@tanstack/react-router";

import type { FormAlertState } from "./field-message";

// A failed save's form-level alert, shared by the console's forms: the title, the messages no
// field took, and after an ended session a link to sign in that returns here.
export function FormAlert({ alert }: Readonly<{ alert: FormAlertState | undefined }>) {
  const { href } = useLocation();
  if (!alert) return null;
  return (
    <Alert status="danger" role="alert">
      <Alert.Content>
        <Alert.Title>{alert.title}</Alert.Title>
        {alert.description ? <Alert.Description>{alert.description}</Alert.Description> : null}
        {alert.signIn ? (
          <Link
            to="/login"
            search={{ returnTo: href }}
            className="font-medium underline underline-offset-4"
          >
            Sign in
          </Link>
        ) : null}
      </Alert.Content>
    </Alert>
  );
}
