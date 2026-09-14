package fr.geoffreyCoulaud.pinryReborn.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * A page size the caller asked for is clamped before the query runs.
 *
 * The bound belongs to the use case: `pageSize=0` answers a page nothing advances past, since the
 * cursor a page carries is taken from its own items, and an unbounded one lets a request read the
 * whole table. Four use cases clamped into `1..PinGetter.MAX_PAGE_SIZE` and three did not, which is
 * the defect this rule closes for the next one written.
 *
 * ## Reach
 *
 * The rule reads every mention of the parameter inside the function that declares it, and each one
 * has to be what `coerceIn` is called on. That is what a search for the word `coerceIn` cannot do:
 * a function clamping one read and forwarding the other carries the word and the defect both.
 *
 * Names, not resolved members, like the rest of this rule set. The parameter is found by the name
 * `pageSize`, so the same bound written on a parameter named otherwise goes unseen; and a member
 * named `pageSize` read on another receiver inside such a function is taken for the parameter and
 * reported. `coerceIn` is a spelling too: `coerceAtLeast(1).coerceAtMost(max)` bounds the same value
 * and is reported.
 *
 * The scope is set in `detekt.yml`, over the use cases alone. Outside them the parameter is a value
 * already bounded travelling to the query: the repositories forward it, and `ModelPaginationHelper`
 * does arithmetic on it.
 */
class PageSizeForwardedUnclamped(
    config: Config,
) : Rule(
        config,
        "A page size that reaches the query unclamped lets a request ask for no rows, or for every row.",
    ) {
    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (function.valueParameters.none { it.name == PAGE_SIZE }) return
        val body = function.bodyExpression ?: return
        body
            .collectDescendantsOfType<KtNameReferenceExpression> { it.getReferencedName() == PAGE_SIZE }
            .filterNot { it.namesAnArgument() || it.isClamped() }
            .forEach { report(Finding(Entity.from(it), MESSAGE)) }
    }

    /** `pageSize = ...` at a call site names a parameter of the callee and reads nothing. */
    private fun KtNameReferenceExpression.namesAnArgument(): Boolean = parent is KtValueArgumentName

    /**
     * Only a receiver can carry the clamp: were the mention the selector of the qualified expression,
     * that selector would be `pageSize` rather than `coerceIn`.
     */
    private fun KtNameReferenceExpression.isClamped(): Boolean =
        (parent as? KtDotQualifiedExpression)?.selectorExpression.endsOnName() == CLAMP

    private companion object {
        private const val PAGE_SIZE = "pageSize"
        private const val CLAMP = "coerceIn"

        private const val MESSAGE =
            "pageSize reaches the repository unclamped, so a request can ask for a page of zero. " +
                "Clamp it with coerceIn."
    }
}
