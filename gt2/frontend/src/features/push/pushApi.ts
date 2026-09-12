import { api, jsonInit } from "../../lib/api";
import type { BrowserSubscription } from "../../lib/push";

const BASE = "/api/push";

export interface PushServerStatus {
  configured: boolean;
  /** The VAPID public key a browser subscribes with. Null when push is off on the server. */
  publicKey: string | null;
  devices: number;
}

export interface PushDevice {
  id: number;
  label: string | null;
  createdAt: string;
  lastSentAt: string | null;
  endpoint: string;
}

export interface PushOutcome {
  sent: number;
  gone: number;
  failed: number;
}

export const getPushStatus = () => api<PushServerStatus>(`${BASE}/status`);

export const listPushDevices = () => api<PushDevice[]>(`${BASE}/subscriptions`);

export const savePushSubscription = (subscription: BrowserSubscription, userAgent: string) =>
  api<{ id: number; devices: number }>(
    `${BASE}/subscriptions`,
    jsonInit("PUT", { ...subscription, userAgent }),
  );

export const deletePushDevice = (id: number) =>
  api(`${BASE}/subscriptions/${id}`, { method: "DELETE" });

/** To this device when its endpoint is known, else to every device. */
export const sendPushTest = (endpoint: string | null) =>
  api<PushOutcome>(`${BASE}/test`, jsonInit("POST", { endpoint }));
