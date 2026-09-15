package fr.geoffreyCoulaud.pinryReborn.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.isPlain
import org.jetbrains.kotlin.psi.psiUtil.plainContent

/**
 * Production raw SQL is a closed set (`docs/specs/2026-09-15-raw-sql-audited.md`, section C). The map
 * below is that set: a fragment, and the reason the query beans cannot express it. A `raw(` call
 * whose argument is not one of those fragments is reported, so a new one cannot be written without
 * its reason being written beside it, in a file the reviewer sees in the diff.
 *
 * An argument that is not a string literal is reported too: `raw(SOME_CONSTANT)` would otherwise be a
 * production raw call the inventory never sees.
 *
 * It freezes fragments and not call sites: a sixth sort strategy reusing `id <= ?` is the same forced
 * call and passes, while a genuinely new shape is a new fragment by construction. What it cannot do
 * is report a fragment that has disappeared, a rule visiting files and never asserting a set was
 * consumed. That costs a stale line in the map and no hole in the guard.
 *
 * Names, not resolved members, like the rest of this rule set: a `raw(` on another receiver is
 * reported too, which is a nuisance rather than a miss.
 */
class RawSqlOutsideInventory(
    config: Config,
) : Rule(
        config,
        "Production raw SQL is a closed set, and a raw( call outside the inventory carries no reason " +
            "for reaching past the query beans.",
    ) {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression.endsOnName() != RAW) return
        val argument = expression.valueArguments.firstOrNull() ?: return
        val fragment = plainLiteralOf(argument.getArgumentExpression())
        if (fragment == null || fragment !in INVENTORY) {
            report(Finding(Entity.from(expression), messageFor(fragment)))
        }
    }

    /** The text of a string literal carrying no interpolation, null for anything else. */
    private fun plainLiteralOf(expression: KtExpression?): String? =
        (expression as? KtStringTemplateExpression)?.takeIf { it.isPlain() }?.plainContent

    private fun messageFor(fragment: String?): String =
        if (fragment == null) {
            "raw( takes something other than a string literal, so the inventory in " +
                "RawSqlOutsideInventory can never see the SQL it runs. Pass the fragment inline."
        } else {
            "raw(\"$fragment\") is not in the inventory in RawSqlOutsideInventory. Production raw SQL " +
                "is a closed set: use the query beans, or add the fragment there with the reason they " +
                "cannot express it."
        }

    private companion object {
        private const val RAW = "raw"

        private const val NO_ORDERED_COMPARISON =
            "The property hierarchy PUuid to PBaseValueEqual to TQPropertyBase carries eq, ne, in, the " +
                "subqueries, isNull and the two orderings, and no ordered comparison; the branch that " +
                "has one is the one PInstant takes through PBaseDate. A keyset cursor needs <= on its " +
                "tiebreaker. ExpressionList.le(\"id\", x) is not the way out: it appends to the query's " +
                "root clause, where eight of these sit inside an and() nested in an or(), so the " +
                "predicate would leave the junction and the cursor would be wrong in silence."

        /** Each fragment production may pass to `raw(`, against the reason the query beans cannot express it. */
        private val INVENTORY =
            mapOf(
                "id <= ?" to NO_ORDERED_COMPARISON,
                "id >= ?" to NO_ORDERED_COMPARISON,
                "id > ?" to NO_ORDERED_COMPARISON,
                "name collate nocase = ?" to
                    "The lookup has to ask what the unique index asks, and that index is " +
                    "(author_id, name collate nocase). ieq asks something else: " +
                    "CaseInsensitiveEqualExpression emits lower(col) = ? and binds value.toLowerCase(), " +
                    "so the column is folded by SQLite and the value by Java. SQLite's lower() is " +
                    "limited to the English alphabet and Java's is full Unicode, so a row `ete` accented " +
                    "is found by an upper-case search and not the reverse; toLowerCase() also takes no " +
                    "locale. And lower(name) is not the indexed expression, so a find-or-create could " +
                    "miss a row the constraint then refuses.",
            )
    }
}
