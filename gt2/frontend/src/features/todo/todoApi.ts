/** Every todo URL. See features/finance/financeApi.ts for why these modules exist. */
import { api, jsonInit } from "../../lib/api";
import type { Todo } from "../../lib/types";

const BASE = "/api/todos";

export const getTodos = (kind?: string) =>
  api<Todo[]>(kind ? `${BASE}?kind=${kind}` : BASE);

export const createTodo = (body: unknown) => api(BASE, jsonInit("POST", body));

export const updateTodo = (id: number, body: unknown) =>
  api(`${BASE}/${id}`, jsonInit("PATCH", body));

export const deleteTodo = (id: number) => api(`${BASE}/${id}`, { method: "DELETE" });
