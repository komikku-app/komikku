package android.annotation

/**
 * android-compat: `android.annotation.SuppressLint`. Lint-only marker; source retention like the SDK,
 * so it has no effect on desktop bytecode.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FILE,
)
@Retention(AnnotationRetention.SOURCE)
annotation class SuppressLint(vararg val value: String)
