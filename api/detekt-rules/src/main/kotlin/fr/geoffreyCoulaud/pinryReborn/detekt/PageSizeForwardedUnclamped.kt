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
 * In a function declaring a `pageSize` parameter, every mention of it is what `coerceIn` is called
 * on. Names, not resolved members, like the rest of this rule set; `detekt.yml` sets the scope.
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

    /** Only a receiver matches: as the selector, the name compared would be `pageSize` itself. */
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
