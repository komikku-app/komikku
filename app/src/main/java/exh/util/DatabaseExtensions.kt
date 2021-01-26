package exh.util

import android.database.Cursor
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetCursor
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetListOfObjects
import com.pushtorefresh.storio.sqlite.operations.get.PreparedGetObject
import com.pushtorefresh.storio.sqlite.operations.put.PreparedPutCollectionOfObjects
import com.pushtorefresh.storio.sqlite.operations.put.PreparedPutObject
import com.pushtorefresh.storio.sqlite.operations.put.PutResult
import com.pushtorefresh.storio.sqlite.operations.put.PutResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun <T> PreparedGetListOfObjects<T>.executeOnIO(): List<T> = withContext(Dispatchers.IO) { executeAsBlocking() }

suspend fun <T> PreparedGetObject<T>.executeOnIO(): T? = withContext(Dispatchers.IO) { executeAsBlocking() }

suspend fun <T> PreparedPutObject<T>.executeOnIO(): PutResult = withContext(Dispatchers.IO) { executeAsBlocking() }

suspend fun <T> PreparedPutCollectionOfObjects<T>.executeOnIO(): PutResults<T> = withContext(Dispatchers.IO) { executeAsBlocking() }

suspend fun PreparedGetCursor.executeOnIO(): Cursor = withContext(Dispatchers.IO) { executeAsBlocking() }
