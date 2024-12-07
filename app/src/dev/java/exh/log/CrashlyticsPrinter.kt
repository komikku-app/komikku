package exh.log

import com.elvishew.xlog.printer.Printer

class CrashlyticsPrinter(private val logLevel: Int) : Printer {
    /**
     * Print log in new line.
     *
     * @param logLevel the level of log
     * @param tag the tag of log
     * @param msg the msg of log
     */
    override fun println(logLevel: Int, tag: String?, msg: String?) = Unit

    companion object {
        fun reportNonFatal(e: Exception) = Unit
    }
}
