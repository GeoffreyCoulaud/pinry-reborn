package fr.geoffreyCoulaud.pinryReborn.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * A row is written inside the transaction that read it.
 *
 * `Persistor.merge` writes every column and only `TaskModel` carries a version, so saving a row read earlier restores
 * that row's whole state, including whatever another actor committed in between. The import lot found nine sites of
 * that one defect before making the read and the write a single fenced pair; the P2 lot found three more on pins and
 * boards (`docs/specs/2026-09-05-p2-debt-elimination.md`, 4.6) and put one generic fence under every feature
 * (`usecases/Fences.kt`).
 *
 * ## Reach
 *
 * The test is **inverted**, and this is the rule's whole shape: a `save*` handed one argument is reported unless that
 * argument is a construction. An insert is legal because a fresh row has no earlier state to restore; everything else
 * names a row that was read somewhere the transaction does not cover. Chasing the shapes that do merge is what the
 * first version tried, keying on a `copy` call written where the save is, and a named local, a scoping function around
 * the copy or one around the save all walked past it. A save taking two arguments (`saveSessionToken(token, hash)`)
 * names no row that was read, and is not the shape.
 *
 * A construction is told from a call by its name starting upper case, Kotlin's own convention, since this rule set runs
 * without type resolution. So does the rest: `save*`, `inTransaction`, `fenced` and `fencedOver` are spellings, not
 * resolved members, and a function named `fenced` elsewhere is taken for the helper. The scope is set in `detekt.yml`,
 * by path, over every use case.
 *
 * ## Three limits, each accepted
 *
 * **A construction is an insert to the rule, whatever it rebuilds.** A row rebuilt field by field from an earlier read,
 * `Pin(id = old.id, ...)`, is a construction here and a merge to the database. No lexical criterion separates the two;
 * type resolution would, at a cost on every gate for a shape nobody writes here (spec D8, `docs/backlog.md` Known
 * limits).
 *
 * **A `save*` passed as a callable reference is outside its sight**, `::save` being no call the rule visits. The four
 * `saveFenced` delegations spell their write as a lambda, `{ save(it) }`, for that reason, and so must the next one.
 *
 * **The rule sees where the write is, not where the read was.** A row read outside and saved inside a transaction
 * passes it: opening a transaction around a row taken before it changes nothing about what the merge restores. That
 * half is a behaviour, held by behaviour tests whose transaction fake answers a read taken outside one with a cancelled
 * row (`UserDataImportRunnerTest`, `UserDataImportCancellerTest`), and by the pin and board sites' cases, whose fence
 * re-reads a recycled row. This rule is the other half: it fails the build on a writer that never opened a transaction
 * at all, which is the shape every one of the twelve sites took.
 *
 * A row built somewhere else and handed over through a property is reported all the same
 * (`mediaRepository.save(created.ingested.media)` in `UserDataImportRunner` and `mediaRepository.save(ingested.media)`
 * in `SetPinMedia`, each suppressed inline with its reason): the rule cannot see where that value came from, and the
 * answer that keeps its reach is to report and let the site say why. Those are the rule's only suppressions, and the
 * function that saves is the function that opens the transaction everywhere else, which is what keeps the fence
 * lexical.
 */
class RowMergedOutsideTransaction(config: Config) :
    Rule(
        config,
        "A row saved outside the transaction that read it restores whatever another actor wrote in " +
            "between, its state included.",
    ) {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!expression.mergesARowReadElsewhere() || expression.insideATransaction()) return
        report(
            Finding(
                Entity.from(expression),
                "This save merges a row read elsewhere, which restores every column that row " +
                    "carried, its state included. Read the row inside the transaction that saves it.",
            )
        )
    }

    /** A `save*` handed one thing that is not a fresh row, which is every way of merging an old one. */
    private fun KtCallExpression.mergesARowReadElsewhere(): Boolean {
        val argument = valueArguments.singleOrNull() ?: return false
        return SAVE.containsMatchIn(calleeExpression.endsOnName()) && !argument.getArgumentExpression().constructsARow()
    }

    /** Upper case is Kotlin's own mark of a constructor, and the only one available without types. */
    private fun KtExpression?.constructsARow(): Boolean =
        when (this) {
            is KtQualifiedExpression -> selectorExpression.constructsARow()
            is KtCallExpression -> CONSTRUCTION.containsMatchIn(calleeExpression.endsOnName())
            else -> false
        }

    /** Lexical, which is what the fence is: the read and the write are one pair or they are not. */
    private fun KtElement.insideATransaction(): Boolean =
        parents.filterIsInstance<KtCallExpression>().any { it.calleeExpression.endsOnName() in BOUNDARIES }

    private companion object {
        /** `save`, `savePin`, `saveBoard`, `saveTag`, `saveUser`: every repository write starts so. */
        private val SAVE = Regex("^save")

        /**
         * `TransactionRunner`'s member and the two generic fences of `usecases/Fences.kt` that open it: a write handed
         * to them as a lambda is inside. Spellings, not resolved members, like `save`.
         */
        private val BOUNDARIES = setOf("inTransaction", "fenced", "fencedOver")

        /** A callee with no name matches nothing here, which is the reading that reports it. */
        private val CONSTRUCTION = Regex("^\\p{Lu}")
    }
}
