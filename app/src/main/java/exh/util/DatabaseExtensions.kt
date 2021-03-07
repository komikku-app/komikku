package exh.util

import android.database.Cursor
import com.pushtorefresh.storio.operations.PreparedOperation
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetCursor
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetListOfObjects
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetObject
import com.pushtorefresh.storio.sqlite.operations.put.PreparedPutCollectionOfObjects
import com.pushtorefresh.storio.sqlite.operations.put.PreparedPutObject
import com.pushtorefresh.storio.sqlite.operations.put.PutResult
import com.pushtorefresh.storio.sqlite.operations.put.PutResults
import eu.kanade.tachiyomi.util.lang.withIOContext

suspend fun <T> PreparedGetListOfObjects<T>.executeOnIO(): List<T> = withIOContext { executeAsBlocking() }

suspend fun <T> PreparedGetObject<T>.executeOnIO(): T? = withIOContext { executeAsBlocking() }

suspend fun <T> PreparedPutObject<T>.executeOnIO(): PutResult = withIOContext { executeAsBlocking() }

suspend fun <T> PreparedPutCollectionOfObjects<T>.executeOnIO(): PutResults<T> = withIOContext { executeAsBlocking() }

suspend fun PreparedGetCursor.executeOnIO(): Cursor = withIOContext { executeAsBlocking() }

suspend fun <T> PreparedOperation<T>.executeOnIO(): T? = withIOContext { executeAsBlocking() }
