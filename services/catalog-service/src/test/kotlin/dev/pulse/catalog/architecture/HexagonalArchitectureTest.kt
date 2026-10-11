package dev.pulse.catalog.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture

/**
 * Enforces the hexagon at build time. Without this, layering erodes within months; with it,
 * a stray `import org.springframework...` in the domain fails CI.
 */
@AnalyzeClasses(packages = ["dev.pulse.catalog"], importOptions = [ImportOption.DoNotIncludeTests::class])
class HexagonalArchitectureTest {

    @ArchTest
    val layers: ArchRule = layeredArchitecture().consideringOnlyDependenciesInLayers()
        .layer("Domain").definedBy("..catalog.domain..")
        .layer("Application").definedBy("..catalog.application..")
        .layer("Adapters").definedBy("..catalog.adapter..")
        .layer("Config").definedBy("..catalog.config..")
        .whereLayer("Adapters").mayOnlyBeAccessedByLayers("Config")
        .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapters", "Config")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapters", "Config")

    @ArchTest
    val domainAndApplicationAreFrameworkFree: ArchRule = noClasses()
        .that().resideInAnyPackage("..catalog.domain..", "..catalog.application..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "com.fasterxml..")

    @ArchTest
    val inboundAdaptersDontTouchOutbound: ArchRule = noClasses()
        .that().resideInAPackage("..adapter.inbound..")
        .should().dependOnClassesThat().resideInAPackage("..adapter.outbound..")
}
