/**
 * Session endpoints.
 *
 * <p>`logout` deliberately uses raw fetch rather than the shared wrapper: the wrapper retries a 401
 * through the refresh endpoint, which is exactly the wrong thing to do when the goal is to stop
 * being logged in.
 */
import { api, jsonInit } from "../../lib/api";

export const me = () => api<{ username: string }>("/api/auth/me");

export const login = (username: string, password: string, otp: string) =>
  api<{ username: string }>("/api/auth/login", jsonInit("POST", { username, password, otp }));

export const logout = () =>
  fetch("/api/auth/logout", { method: "POST", credentials: "same-origin" });
