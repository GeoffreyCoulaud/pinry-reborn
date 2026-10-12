package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoAnnotationDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.ext.list.properties
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.ext.list.withFunctions
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.ext.list.withProperty
import com.lemonappdev.konsist.api.ext.list.withType
import com.lemonappdev.konsist.api.ext.list.withoutAnnotation
import com.lemonappdev.konsist.api.ext.list.withoutAnnotationOf
import com.lemonappdev.konsist.api.verify.assertEmpty
import com.lemonappdev.konsist.api.verify.assertNotEmpty
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guardrail for operator decision B1: the `models` package (+ `.bases`) is excluded from the Kover branch-coverage gate
 * because Ebean bytecode-enhancement injects untestable branches into entity classes. This test keeps that exclusion
 * safe by enforcing that every class in the package stays a pure field-storage entity, so no hand-written branchy logic
 * can hide there.
 *
 * Each rule reads as "the set of offending classes is empty". The separate non-empty check guards against a mis-scoped
 * filter silently passing every `assertEmpty` on an empty list.
 *
 * Scoped to `scopeFromProduction`, not `scopeFromModule`: the latter also walks this very test's source set, and this
 * file itself resides in `..models..`, so it would match its own filter.
 */
class ModelsPackageArchTest {
    private val modelClasses: List<KoClassDeclaration> =
        Konsist.scopeFromProduction(moduleName = "api-persistence-sqlite")
            .classes()
            .withPackage("..persistence.sqlite.models..")

    @Test
    fun `Given the models package, Then it holds classes (the scope is not mis-configured)`() {
        modelClasses.assertNotEmpty()
    }

    @Test
    fun `Given the coverage-excluded models package, Then no class is a non-entity`() {
        modelClasses.withoutAnnotationOf(Entity::class, MappedSuperclass::class).assertEmpty()
    }

    @Test
    fun `Given the coverage-excluded models package, Then no class declares functions`() {
        modelClasses.withFunctions().assertEmpty()
    }

    @Test
    fun `Given the coverage-excluded models package, Then no class has a custom property accessor`() {
        modelClasses.withProperty { it.hasGetter || it.hasSetter }.assertEmpty()
    }

    /** Ebean's migration generation breaks in silence on an inline value class (`api/AGENTS.md`, Data modelling). */
    private fun valueClassProperties(models: List<KoClassDeclaration>): List<KoPropertyDeclaration> =
        models.properties().withType { it.sourceDeclaration?.asClassDeclaration()?.hasValueModifier == true }

    @Test
    fun `Given a fixture model holding a value class, Then the value class rule flags its property`() {
        val fixture =
            Konsist.scopeFromFile("api-persistence-sqlite/src/test/resources/konsist/ValueClassPropertyModel.kt")
                .classes()
                .withPackage("..persistence.sqlite.models..")
        assertEquals(listOf("address"), valueClassProperties(fixture).map { it.name })
    }

    @Test
    fun `Given the models package, Then no property's type is a value class`() {
        valueClassProperties(modelClasses).assertEmpty()
    }

    /** The entities whose `@Table` is not the name their class gives (ADR 0058, decision 5), or that carry none. */
    private fun misnamedTables(models: List<KoClassDeclaration>): List<KoClassDeclaration> =
        models.withAnnotationOf(Entity::class).filterNot { tableOf(it) == expectedTableOf(it.name) }

    private fun expectedTableOf(className: String): String =
        KEPT_TABLE_NAMES[className]?.table ?: pluralOf(snakeCaseOf(className.removeSuffix("Model")))

    private fun snakeCaseOf(name: String): String = name.replace(Regex("(?<!^)([A-Z])"), "_$1").lowercase()

    private fun pluralOf(noun: String): String = if (noun.contains(Regex("(s|sh|ch|x)$"))) "${noun}es" else "${noun}s"

    private fun tableOf(model: KoClassDeclaration): String? =
        model.annotations.firstOrNull { it.name == "Table" }?.let { nameArgumentOf(it) }

    private fun nameArgumentOf(annotation: KoAnnotationDeclaration): String? =
        annotation.arguments.firstOrNull { it.name == "name" }?.value

    /** The `@Index` names that do not start with their entity's table name (ADR 0058, decision 5). */
    private fun misnamedIndexes(models: List<KoClassDeclaration>): List<String> =
        models.withAnnotationOf(Entity::class).flatMap { model ->
            val prefix = Regex("(ix|ux|uq|ck)_${tableOf(model)}_\\w+")
            model.annotations
                .filter { it.name == "Index" }
                .mapNotNull { nameArgumentOf(it) }
                .filterNot { it.matches(prefix) }
        }

    @Test
    fun `Given a fixture model whose table is pin_tag_model, Then the table rule flags it`() {
        val fixture =
            Konsist.scopeFromFile("api-persistence-sqlite/src/test/resources/konsist/TableNameModel.kt").classes()
        assertEquals(listOf("PinTagModel"), misnamedTables(fixture).map { it.name })
    }

    @Test
    fun `Given the models package, Then every entity's table is the name its class gives`() {
        misnamedTables(modelClasses).assertEmpty()
    }

    @Test
    fun `Given a fixture model with an index named after another table, Then the index rule flags it alone`() {
        val fixture =
            Konsist.scopeFromFile("api-persistence-sqlite/src/test/resources/konsist/IndexNameModel.kt").classes()
        assertEquals(listOf("ix_pin_tag_model_tag"), misnamedIndexes(fixture))
    }

    @Test
    fun `Given the models package, Then every index name starts with its table's name`() {
        assertEquals(emptyList<String>(), misnamedIndexes(modelClasses))
    }

    /** The relations that do not declare what their parent's deletion does (ADR 0058, decision 4). */
    private fun undeclaredDeletions(models: List<KoClassDeclaration>): List<String> =
        models
            .properties()
            .withAnnotationOf(ManyToOne::class)
            .withoutAnnotation {
                it.name == "DbForeignKey" &&
                    it.hasArgumentWithName("onDelete") &&
                    !it.hasArgumentWithName("noConstraint")
            }
            .map { it.name }

    @Test
    fun `Given a fixture model with keys declaring no deletion or no constraint, Then the key rule flags both`() {
        val fixture =
            Konsist.scopeFromFile("api-persistence-sqlite/src/test/resources/konsist/ForeignKeyModel.kt").classes()
        assertEquals(listOf("tag", "board"), undeclaredDeletions(fixture))
    }

    @Test
    fun `Given the models package, Then every relation declares what its parent's deletion does`() {
        assertEquals(emptyList<String>(), undeclaredDeletions(modelClasses))
    }

    private data class KeptTableName(val table: String, val reason: String)

    private companion object {
        /** The models whose table keeps another name, each with its reason (`api/AGENTS.md`, Gotchas). */
        private val KEPT_TABLE_NAMES =
            mapOf("MediaModel" to KeptTableName(table = "media", reason = "The word is its own plural."))
    }
}
