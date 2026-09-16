import { setupServer } from "msw/node";

import { createMswHandlers } from "@/api/generated/msw.gen";

export const server = setupServer(...createMswHandlers().all());
