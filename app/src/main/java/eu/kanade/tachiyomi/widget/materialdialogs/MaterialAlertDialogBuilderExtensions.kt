package eu.kanade.tachiyomi.widget.materialdialogs

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.presentation.theme.colorscheme.AndroidViewColorScheme
import eu.kanade.tachiyomi.databinding.DialogStubTextinputBinding

// KMK -->
@Suppress("UnusedReceiverParameter")
fun MaterialAlertDialogBuilder.binding(context: Context): DialogStubTextinputBinding {
    return DialogStubTextinputBinding.inflate(LayoutInflater.from(context))
}

fun AlertDialog.dismissDialog() {
    if (isShowing) {
        dismiss()
    }
}

fun DialogStubTextinputBinding.setTitle(title: String): DialogStubTextinputBinding {
    alertTitle.text = title
    return this
}

fun DialogStubTextinputBinding.setPositiveButton(text: String, onClick: (String) -> Unit): DialogStubTextinputBinding {
    positiveButton.text = text

    positiveButton.setOnClickListener {
        val textEdit = textInputLayout.editText
        onClick(textEdit?.text?.toString() ?: "")
    }
    return this
}

fun DialogStubTextinputBinding.setNegativeButton(text: String, onClick: () -> Unit): DialogStubTextinputBinding {
    negativeButton.text = text

    negativeButton.setOnClickListener {
        onClick()
    }
    return this
}

@Suppress("unused")
fun DialogStubTextinputBinding.setHint(hint: String? = null): DialogStubTextinputBinding {
    textInputLayout.hint = hint
    return this
}

fun DialogStubTextinputBinding.setTextEdit(prefill: String? = null): DialogStubTextinputBinding {
    // KMK <--
    textInputLayout.editText?.apply {
        setText(prefill, TextView.BufferType.EDITABLE)
        post {
            requestFocusFromTouch()
            context.getSystemService<InputMethodManager>()?.showSoftInput(this, 0)
        }
    }
    // KMK -->
    return this
}

fun DialogStubTextinputBinding.setColors(colorScheme: AndroidViewColorScheme): DialogStubTextinputBinding {
    colorScheme.setTextInputLayoutColor(textInputLayout)
    colorScheme.setEditTextColor(textInputEdit)

    alertTitle.setTextColor(colorScheme.textColor)
    positiveButton.setTextColor(colorScheme.iconColor)
    negativeButton.setTextColor(colorScheme.iconColor)

    root.background = RoundedCornerDrawable(color = colorScheme.dialogBgColor)

    return this
}

class RoundedCornerDrawable(val color: Int, private val cornerRadius: Float = 72f) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        paint.color = color
        canvas.drawRoundRect(
            RectF(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat()),
            cornerRadius,
            cornerRadius,
            paint,
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }
}
// KMK <--
