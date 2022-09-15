package exh.log

import com.elvishew.xlog.XLog
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

fun OkHttpClient.Builder.maybeInjectEHLogger(): OkHttpClient.Builder {
    if (EHLogLevel.shouldLog(EHLogLevel.EXTREME)) {
        val logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger { message ->
            try {
                Json.decodeFromString<Any>(message)
                XLog.tag("||EH-NETWORK-JSON").json(message)
            } catch (ex: Exception) {
                XLog.tag("||EH-NETWORK").disableBorder().d(message)
            }
        }
        return addInterceptor(HttpLoggingInterceptor(logger).apply { level = HttpLoggingInterceptor.Level.BODY })
    }
    return this
}
