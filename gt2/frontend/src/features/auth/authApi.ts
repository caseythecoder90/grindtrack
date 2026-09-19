/**
 * Session endpoints, and the owner's view of who can sign in.
 *
 * <p>`logout` deliberately uses raw fetch rather than the shared wrapper: the wrapper retries a 401
 * through the refresh endpoint, which is exactly the wrong thing to do when the goal is to stop
 * being logged in.
 */
import { api, jsonInit } from "../../lib/api";

export type Role = "OWNER" | "PARTNER";

/** Who is signed in: the body of login, refresh and `/me`. The page renders by the role. */
export interface Session {
  username: string;
  role: Role;
}

export interface Account {
  id: number;
  username: string;
  role: Role;
  createdAt: string | null;
}

/** A partner just made, with the authenticator secret — the one time it is ever sent. */
export interface PartnerCreated extends Account {
  totpSecret: string;
  otpauthUri: string;
}

export const me = () => api<Session>("/api/auth/me");

export const login = (username: string, password: string, otp: string, trustDevice: boolean) =>
  api<Session>("/api/auth/login", jsonInit("POST", { username, password, otp, trustDevice }));

/**
 * Whether this browser still needs an authenticator code.
 *
 * <p>Asked before anything is typed so the form can drop the field rather than ask for something
 * the server will not check. The device cookie is HttpOnly, so this is the only way the page can
 * know -- and it is the server's answer either way, since the form's opinion decides nothing.
 */
export const deviceTrusted = () =>
  api<{ trusted: boolean; count: number }>("/api/auth/device");

/** Forget every remembered device. The answer to a lost phone. */
export const forgetDevices = () =>
  api<{ trusted: boolean; count: number }>("/api/auth/devices/forget", { method: "POST" });

export const logout = () =>
  fetch("/api/auth/logout", { method: "POST", credentials: "same-origin" });

/**
 * End every session on every device, this one included.
 *
 * <p>Goes through the wrapper, unlike `logout`: it is authenticated, so an access cookie that has
 * just lapsed should be renewed and the request replayed rather than abandoned. Other devices
 * find out when their own access cookie next lapses, within the access-token lifetime.
 */
export const logoutEverywhere = () =>
  api<{ status: string; sessionsEnded: number }>("/api/auth/logout-all", { method: "POST" });

// ---- accounts: the owner only; a partner is refused these with a 403 ----

export const listAccounts = () => api<Account[]>("/api/auth/users");

export const createPartner = (username: string, password: string) =>
  api<PartnerCreated>("/api/auth/users", jsonInit("POST", { username, password }));

export const resetPartnerPassword = (id: number, password: string) =>
  api<Account>(`/api/auth/users/${id}/password`, jsonInit("PUT", { password }));

/** Their sessions ended and their devices forgotten: the next sign-in wants the code again. */
export const endPartnerSessions = (id: number) =>
  api<{ status: string; sessionsEnded: number }>(`/api/auth/users/${id}/logout-all`, {
    method: "POST",
  });
