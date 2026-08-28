package dev.grindtrack.finance.domain;

import java.math.BigDecimal;

/**
 * One row of a spending rollup: a category or merchant, what it came to, and over how many
 * transactions.
 *
 * <p>{@code total} keeps the app's sign convention — spending is negative — rather than being
 * flipped here. The one place a number changes sign is the screen that displays it, so nothing in
 * between has to remember which convention it is holding.
 *
 * @param label the category or merchant name; null means uncategorized, which the UI must show
 *     rather than hide, since a large unlabelled slice is the signal that rules need writing
 */
public record CategoryTotal(String label, BigDecimal total, long count) {}
