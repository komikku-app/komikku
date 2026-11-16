package mihon.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

/** The module whose sources are borrowed. */
const val BORROWING_SOURCE_PROJECT = ":app"

/** The module that compiles the borrowed sources for Android and desktop. */
const val BORROWING_TARGET_PROJECT = ":app-shared"

/** Source root of [BORROWING_SOURCE_PROJECT] the allowlisted paths are relative to. */
const val BORROWING_SOURCE_DIR = "src/main/java"

/** Name of the verification task registered in [BORROWING_SOURCE_PROJECT] (also run by `check`). */
const val VERIFY_BORROWED_SOURCES_TASK = "verifyBorrowedSources"

/**
 * KMK: allowlist of `app` source files compiled by `:app-shared` instead of `app` ("source
 * borrowing", docs/kmp-desktop/PLAN.md D5). Paths are relative to `app/src/main/java` and name
 * single `.kt` files; the files stay at their upstream paths.
 */
class BorrowedSources internal constructor() {
    internal val shared = sortedSetOf<String>()
    internal val androidOnly = sortedSetOf<String>()
    internal val perPlatform = sortedSetOf<String>()
    internal val desktopCopy = sortedSetOf<String>()

    /** Compiled once in `jvmSharedMain`, i.e. for Android and desktop. The common case. */
    fun shared(vararg paths: String) = add(shared, paths)

    /**
     * Compiled in `androidMain` only: the file needs Android APIs. `:app-shared` provides a desktop
     * counterpart with the same declarations in `src/desktopMain/kotlin` when desktop needs them.
     */
    fun androidOnly(vararg paths: String) = add(androidOnly, paths)

    /**
     * Compiled separately by `androidMain` and `desktopMain` (same file, two compilations): for files
     * that call `androidOnly` declarations or their desktop counterparts, which `jvmSharedMain`
     * cannot see.
     */
    fun perPlatform(vararg paths: String) = add(perPlatform, paths)

    /**
     * Compiled by `app` for Android exactly as before (not excluded) **and**, as a copy, by
     * `:app-shared`'s `desktopMain`: for files whose Android dependencies are too entangled to move
     * them out of `app` yet (download manager, reader activity, Android-only sources). Desktop
     * counterparts of the symbols they need that are not otherwise available live in
     * `app-shared/src/desktopMain/kotlin`. Android behaviour and compilation are unaffected; the
     * desktop build follows upstream edits to the file automatically.
     */
    fun desktopCopy(vararg paths: String) = add(desktopCopy, paths)

    internal val all: Set<String> get() = shared + androidOnly + perPlatform + desktopCopy

    /** Files that `app` must not compile (all kinds except [desktopCopy]). */
    internal val excludedFromApp: Set<String> get() = shared + androidOnly + perPlatform

    private fun add(set: MutableSet<String>, paths: Array<out String>) {
        paths.forEach { path ->
            require(path.endsWith(".kt") && !path.startsWith("/") && '*' !in path) {
                "Borrowed source must be a relative .kt file path without wildcards: $path"
            }
            require(path !in all) { "Borrowed source listed twice: $path" }
            set += path
        }
    }
}

/** The allowlist script (relative to the root project). */
const val BORROWING_ALLOWLIST = "app-shared/borrowed-app-sources.gradle.kts"

/**
 * Applies the source borrowing allowlist ([BORROWING_ALLOWLIST]) to the current project, which must
 * be [BORROWING_SOURCE_PROJECT] or [BORROWING_TARGET_PROJECT]. See [borrowAppSources].
 */
fun Project.applyAppSourceBorrowing() {
    apply(mapOf("from" to rootProject.file(BORROWING_ALLOWLIST)))
    fun list(kind: String): List<String> {
        val value = extensions.extraProperties.get("borrowedAppSources.$kind")
        return (value as List<*>).map { it as String }
    }
    borrowAppSources {
        shared(*list("shared").toTypedArray())
        androidOnly(*list("androidOnly").toTypedArray())
        perPlatform(*list("perPlatform").toTypedArray())
        desktopCopy(*list("desktopCopy").toTypedArray())
    }
}

/**
 * Applies a borrowing allowlist to the current project:
 *
 * - in `:app-shared`: one `Sync` task per kind copies the allowlisted files from
 *   `app/src/main/java` to `build/generated/borrowed/<kind>/kotlin`, which is added to
 *   `jvmSharedMain` (shared), `androidMain` (androidOnly) or both `androidMain` and `desktopMain`
 *   (perPlatform). Copies (instead of pointing the source sets at `app/src/main/java` with include
 *   filters) keep the IDE from treating the whole app source root as part of `:app-shared`.
 *   A sync fails if an allowlisted file is missing.
 * - in `:app`: every Kotlin compile task excludes the allowlisted paths (except `desktopCopy` ones), and
 *   [VERIFY_BORROWED_SOURCES_TASK] (wired into `check`) checks, against the real compile task
 *   inputs, that every allowlisted file exists and is compiled by exactly one module: not by `app`'s
 *   `compileDebugKotlin`, and by `:app-shared`'s Android and/or desktop compilation as its kind says.
 */
fun Project.borrowAppSources(configure: BorrowedSources.() -> Unit) {
    val borrowed = BorrowedSources().apply(configure)
    when (path) {
        BORROWING_SOURCE_PROJECT -> excludeBorrowedSources(borrowed)
        BORROWING_TARGET_PROJECT -> addBorrowedSources(borrowed)
        else -> throw GradleException("The source borrowing allowlist only applies to $BORROWING_SOURCE_PROJECT and $BORROWING_TARGET_PROJECT, not $path")
    }
}

private enum class BorrowKind(val dirName: String) {
    Shared("shared"),
    AndroidOnly("androidOnly"),
    PerPlatform("perPlatform"),
    DesktopCopy("desktopCopy"),
    ;

    fun paths(borrowed: BorrowedSources): Set<String> = when (this) {
        Shared -> borrowed.shared
        AndroidOnly -> borrowed.androidOnly
        PerPlatform -> borrowed.perPlatform
        DesktopCopy -> borrowed.desktopCopy
    }

    /** Compiled by `:app-shared` for Android (otherwise `app` compiles it, for [DesktopCopy]). */
    val compiledForAndroid: Boolean get() = this != DesktopCopy
    val compiledForDesktop: Boolean get() = this != AndroidOnly

    val syncTaskName: String get() = "syncBorrowed${dirName.replaceFirstChar(Char::uppercase)}Sources"
}

private fun Project.borrowedDir(kind: BorrowKind): File =
    layout.buildDirectory.dir("generated/borrowed/${kind.dirName}/kotlin").get().asFile

private fun Project.addBorrowedSources(borrowed: BorrowedSources) {
    val appSourceDir = rootProject.project(BORROWING_SOURCE_PROJECT).file(BORROWING_SOURCE_DIR)
    val syncTasks = BorrowKind.entries.associateWith { kind ->
        val paths = kind.paths(borrowed).toList()
        tasks.register<Sync>(kind.syncTaskName) {
            group = "build"
            description = "Copies the app sources borrowed as '${kind.dirName}' into this module (PLAN.md D5)."
            inputs.property("paths", paths)
            from(appSourceDir) { include(paths) }
            into(borrowedDir(kind))
            doFirst {
                val missing = paths.filterNot { appSourceDir.resolve(it).isFile }
                if (missing.isNotEmpty()) {
                    throw GradleException("Borrowed app sources do not exist (fix the allowlist):\n  " + missing.joinToString("\n  "))
                }
            }
        }
    }
    val kmp = extensions.getByType<KotlinMultiplatformExtension>()
    fun TaskProvider<Sync>.output() = map { it.destinationDir }
    kmp.sourceSets.named("${JVM_SHARED_GROUP}Main") { kotlin.srcDir(syncTasks.getValue(BorrowKind.Shared).output()) }
    kmp.sourceSets.named("androidMain") {
        kotlin.srcDir(syncTasks.getValue(BorrowKind.AndroidOnly).output())
        kotlin.srcDir(syncTasks.getValue(BorrowKind.PerPlatform).output())
    }
    kmp.sourceSets.named("${DESKTOP_TARGET}Main") {
        kotlin.srcDir(syncTasks.getValue(BorrowKind.PerPlatform).output())
        kotlin.srcDir(syncTasks.getValue(BorrowKind.DesktopCopy).output())
    }
    // `srcDir(provider)` does not carry the producing task for every consumer; make it explicit.
    tasks.withType<KotlinCompile>().configureEach { dependsOn(syncTasks.values) }
}

private fun Project.excludeBorrowedSources(borrowed: BorrowedSources) {
    val all = borrowed.all.toList()
    val excluded = borrowed.excludedFromApp.toList()
    tasks.withType<KotlinCompile>().configureEach { exclude(excluded) }

    evaluationDependsOn(BORROWING_TARGET_PROJECT)
    val verify = tasks.register(VERIFY_BORROWED_SOURCES_TASK) {
        group = "verification"
        description = "Checks that every borrowed app source exists and is compiled by exactly one module (PLAN.md D5)."
        val target = rootProject.project(BORROWING_TARGET_PROJECT)
        dependsOn(BorrowKind.entries.map { "${target.path}:${it.syncTaskName}" })
        doLast {
            val appSourceDir = file(BORROWING_SOURCE_DIR)
            val errors = mutableListOf<String>()
            all.filterNot { appSourceDir.resolve(it).isFile }
                .forEach { errors += "missing in app/$BORROWING_SOURCE_DIR: $it" }

            fun KotlinCompile.sourceFiles() = sources.files.map { it.canonicalFile }.toSet()

            val appCompiled = tasks.withType<KotlinCompile>().getByName("compileDebugKotlin").sourceFiles()
            // Guard against a vacuous check (e.g. compile task inputs no longer exposed this way).
            if (appCompiled.none { it.startsWith(appSourceDir.canonicalFile) }) {
                errors += "cannot inspect app's compileDebugKotlin sources (no file from $BORROWING_SOURCE_DIR)"
            }
            excluded.filter { appSourceDir.resolve(it).canonicalFile in appCompiled }
                .forEach { errors += "compiled by both modules (not excluded from app): $it" }
            borrowed.desktopCopy.filterNot { appSourceDir.resolve(it).canonicalFile in appCompiled }
                .forEach { errors += "desktop copy no longer compiled by app for Android: $it" }

            val targetCompile = target.tasks.withType<KotlinCompile>()
            val androidCompiled = targetCompile.getByName("compileDebugKotlinAndroid").sourceFiles()
            val desktopCompiled = targetCompile.getByName("compileKotlin${DESKTOP_TARGET.replaceFirstChar(Char::uppercase)}").sourceFiles()
            BorrowKind.entries.forEach { kind ->
                val dir = target.borrowedDir(kind)
                kind.paths(borrowed).forEach { path ->
                    val copy = dir.resolve(path).canonicalFile
                    if (!kind.compiledForAndroid && copy in androidCompiled) {
                        errors += "desktop copy also compiled by ${target.path} for Android: $path"
                    }
                    if (kind.compiledForAndroid && copy !in androidCompiled) {
                        errors += "not compiled by ${target.path} for Android (${kind.dirName}): $path"
                    }
                    if (kind.compiledForDesktop && copy !in desktopCompiled) {
                        errors += "not compiled by ${target.path} for desktop (${kind.dirName}): $path"
                    }
                    if (!kind.compiledForDesktop && copy in desktopCompiled) {
                        errors += "android-only source compiled for desktop: $path"
                    }
                }
            }
            if (errors.isNotEmpty()) {
                throw GradleException("Source borrowing check failed:\n  " + errors.joinToString("\n  "))
            }
            logger.lifecycle(
                "Source borrowing: ${excluded.size} app files compiled by ${target.path} only, " +
                    "${borrowed.desktopCopy.size} also copied for its desktop target",
            )
        }
    }
    tasks.named("check") { dependsOn(verify) }
}
