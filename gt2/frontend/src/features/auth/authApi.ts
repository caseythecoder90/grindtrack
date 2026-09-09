/**
 * Session endpoints.
 *
 * <p>`logout` deliberately uses raw fetch rather than the shared wrapper: the wrapper retries a 401
 * through the refresh endpoint, which is exactly the wrong thing to do when the goal is to stop
 * being logged in.
 */
import { api, jsonInit } from "../../lib/api";

export const me = () => api<{ username: string }>("/api/auth/me");

export const login = (username: string, password: string, otp: string, trustDevice: boolean) =>
  api<{ username: string }>(
    "/api/auth/login",
    jsonInit("POST", { username, password, otp, trustDevice }),
  );

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
