package exh.eh.tags

interface TagList {
    fun getTags1(): List<String>

    fun getTags2(): List<String> = emptyList()

    fun getTags3(): List<String> = emptyList()

    fun getTags4(): List<String> = emptyList()

    fun getTags() = listOf(
        getTags1(),
        getTags2(),
        getTags3(),
        getTags4(),
    )
}
