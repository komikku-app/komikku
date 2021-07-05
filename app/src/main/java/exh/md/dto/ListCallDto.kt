package exh.md.dto

interface ListCallDto<T> {
    val limit: Int
    val offset: Int
    val total: Int
    val results: List<T>
}
