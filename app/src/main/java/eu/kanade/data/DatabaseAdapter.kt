package eu.kanade.data

import com.squareup.sqldelight.ColumnAdapter
import java.util.Date

val dateAdapter = object : ColumnAdapter<Date, Long> {
    override fun decode(databaseValue: Long): Date = Date(databaseValue)
    override fun encode(value: Date): Long = value.time
}

private const val listOfStringsSeparator = ", "
val listOfStringsAdapter = object : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String) =
        if (databaseValue.isEmpty()) {
            listOf()
        } else {
            databaseValue.split(listOfStringsSeparator)
        }
    override fun encode(value: List<String>) = value.joinToString(separator = listOfStringsSeparator)
}

// SY -->
private const val listOfStringsAndSeparator = " & "
val listOfStringsAndAdapter = object : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String) =
        if (databaseValue.isEmpty()) {
            emptyList()
        } else {
            databaseValue.split(listOfStringsAndSeparator)
        }
    override fun encode(value: List<String>) = value.joinToString(separator = listOfStringsAndSeparator)
}
// SY <--
