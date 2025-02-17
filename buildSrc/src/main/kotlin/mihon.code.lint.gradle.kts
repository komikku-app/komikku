import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.diffplug.spotless")
}

val libs = the<LibrariesForLibs>()

val xmlFormatExclude = buildList(2) {
    add("**/build/**/*.xml")

    projectDir
        .resolve("src/commonMain/moko-resources")
        .takeIf { it.isDirectory }
        ?.let(::fileTree)
        ?.matching { exclude("/base/**") }
        ?.let(::add)
}
    .toTypedArray()

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("**/build/**/*.kt")
        ktlint(libs.ktlint.core.get().version)
            // Avoid ktlint failed with messy code from SY
//            .editorConfigOverride(mapOf(
//                "ktlint_standard_max-line-length" to "disabled",
//                "ktlint_standard_argument-list-wrapping" to "disabled",
//                "ktlint_standard_comment-wrapping" to "disabled",
//                "ktlint_standard_type-argument-comment" to "disabled",
//                "ktlint_standard_value-argument-comment" to "disabled",
//                "ktlint_standard_value-parameter-comment" to "disabled",
//                "ktlint_standard_property-naming" to "disabled",
//
//                "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
//                "ktlint_standard_class-signature" to "disabled",
//                "ktlint_standard_discouraged-comment-location" to "disabled",
//                "ktlint_standard_function-expression-body" to "disabled",
//                "ktlint_standard_function-signature" to "disabled",
//            ))
            .editorConfigOverride(mapOf(
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                "ktlint_standard_class-signature" to "disabled",
                "ktlint_standard_discouraged-comment-location" to "disabled",
                "ktlint_standard_function-expression-body" to "disabled",
                "ktlint_standard_function-signature" to "disabled",
                "ktlint_standard_max-line-length" to "disabled",
                // "ktlint_standard_comment-wrapping" to "disabled",
                "ktlint_standard_type-argument-comment" to "disabled",
                "ktlint_standard_value-argument-comment" to "disabled",
                "ktlint_standard_value-parameter-comment" to "disabled",
            ))
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("xml") {
        target("**/*.xml")
        targetExclude(*xmlFormatExclude)
        trimTrailingWhitespace()
        endWithNewline()
    }
}
