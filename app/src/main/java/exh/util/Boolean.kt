package exh.util

infix fun <T : Comparable<T>> T.over(other: T) = this > other

infix fun <T : Comparable<T>> T.overEq(other: T) = this >= other

infix fun <T : Comparable<T>> T.under(other: T) = this < other

infix fun <T : Comparable<T>> T.underEq(other: T) = this <= other
