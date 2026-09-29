package exh.log

import com.elvishew.xlog.Logger

class ResettableLogger(private val resolver: () -> Logger?) {
    private var logger: Logger? = null

    @Synchronized
    operator fun invoke(): Logger? {
        return logger ?: run {
            logger = resolver()
            logger
        }
    }

    @Synchronized
    fun reset() {
        logger = null
    }
}
