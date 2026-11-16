/*
 * Intentionally in the default package: build scripts are compiled in the default package too, so
 * this accessor is visible in every `build.gradle.kts` without an import and keeps the upstream
 * `compose.*` catalog references (e.g. `implementation(compose.foundation)`) compiling unchanged.
 *
 * The version catalog itself is registered as `composeLibs` (see settings.gradle.kts) because a
 * catalog named `compose` is exposed as a project extension called `compose`, which makes the
 * `org.jetbrains.compose` plugin fail with "Cannot add extension with name 'compose'".
 *
 * In modules that apply `org.jetbrains.compose`, this accessor still wins over the generated
 * `compose` extension accessor (same-package declarations take precedence over the Kotlin DSL's
 * star-imported accessors), so the JetBrains extension must be accessed by type, e.g.
 * `extensions.configure<org.jetbrains.compose.ComposeExtension> { ... }`.
 */

import org.gradle.accessors.dm.LibrariesForComposeLibs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the

val Project.compose: LibrariesForComposeLibs
    get() = the<LibrariesForComposeLibs>()
