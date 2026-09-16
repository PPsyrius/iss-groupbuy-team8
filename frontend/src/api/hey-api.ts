import type { CreateClientConfig } from './generated/client.gen'

// The committed contract's servers[0].url is the export harness (http://localhost:18080).
// Same-origin deployment: every call is relative to the page origin.
export const createClientConfig: CreateClientConfig = (config) => ({
  ...config,
  baseUrl: '/',
  credentials: 'same-origin',
})
