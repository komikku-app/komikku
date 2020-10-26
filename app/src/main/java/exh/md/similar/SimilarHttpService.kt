package exh.md.similar

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Streaming
import uy.kohesive.injekt.injectLazy

interface SimilarHttpService {
    companion object {
        private val client by lazy {
            val network: NetworkHelper by injectLazy()
            network.client.newBuilder()
                // unzip interceptor which will add the correct headers
                .addNetworkInterceptor { chain ->
                    val originalResponse = chain.proceed(chain.request())
                    originalResponse.newBuilder()
                        .header("Content-Encoding", "gzip")
                        .header("Content-Type", "application/json")
                        .build()
                }
                .build()
        }

        @ExperimentalSerializationApi
        fun create(): SimilarHttpService {
            // actual builder, which will parse the underlying json file
            val adapter = Retrofit.Builder()
                .baseUrl("https://raw.githubusercontent.com")
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
                .client(client)
                .build()

            return adapter.create(SimilarHttpService::class.java)
        }
    }

    @Streaming
    @GET("/goldbattle/MangadexRecomendations/master/output/mangas_compressed.json.gz")
    fun getSimilarResults(): Call<ResponseBody>
}
