/**
 * Created by Edsuns@qq.com on 2021/1/12.
 */

package sample

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

/**
 * Try to hide the keyboard and returns whether it worked
 */
fun View.hideKeyboard(): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.hideSoftInputFromWindow(windowToken, 0)
}
