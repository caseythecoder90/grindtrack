/**
 * Suggested categories.
 *
 * <p>A starting vocabulary, not a constraint — the field is free text on both the server and the
 * form, so anything typed here works. These exist so the first fifty rows get filed consistently
 * instead of ending up as "Food", "food" and "Groceries/food", which is what makes a rollup
 * useless six months later.
 *
 * <p>Deliberately short. A list long enough to cover every case is a list nobody reads to the end
 * of, and the categories that matter are the ones big enough to change a decision.
 */
export const SUGGESTED_CATEGORIES = [
  "Rent",
  "Groceries",
  "Dining",
  "Utilities",
  "Phone & Internet",
  "Transport",
  "Fuel",
  "Car",
  "Insurance",
  "Health",
  "Subscriptions",
  "Shopping",
  "Entertainment",
  "Travel",
  "Home",
  "Pets",
  "Gifts",
  "Fees & Interest",
  "Education",
  "Other",
] as const;

/** Categories already in use, merged with the suggestions and de-duplicated, for a datalist. */
export function categoryOptions(inUse: (string | null)[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const value of [...inUse, ...SUGGESTED_CATEGORIES]) {
    const name = value?.trim();
    if (!name) continue;
    const key = name.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(name);
  }
  return out;
}
