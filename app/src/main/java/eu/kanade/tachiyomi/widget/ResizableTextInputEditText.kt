package eu.kanade.tachiyomi.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import eu.kanade.tachiyomi.R

class ResizableTextInputEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.editTextStyle
) : TextInputEditText(context, attrs, defStyleAttr) {

    private val resizeHandlePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.primary)
        style = Paint.Style.FILL
    }
    
    private val resizeHandleSize = 24f
    private val resizeHandleRect = RectF()
    private var isResizing = false
    private var originalWidth = 0
    private var originalHeight = 0
    private var lastX = 0f
    private var lastY = 0f

    init {
        // 启用文本滚动
        isScrollContainer = true
        setHorizontallyScrolling(true)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        originalWidth = w
        originalHeight = h
        updateResizeHandleRect()
    }

    private fun updateResizeHandleRect() {
        resizeHandleRect.set(
            width - resizeHandleSize,
            height - resizeHandleSize,
            width.toFloat(),
            height.toFloat()
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 绘制调整大小手柄
        canvas.drawRect(resizeHandleRect, resizeHandlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (resizeHandleRect.contains(event.x, event.y)) {
                    isResizing = true
                    lastX = event.x
                    lastY = event.y
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isResizing) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    
                    // 计算新的宽度（最小为原始宽度的1.5倍，最大为3倍）
                    val newWidth = (width + dx).coerceIn(
                        (originalWidth * 1.5f).toInt(),
                        (originalWidth * 3f).toInt()
                    )
                    
                    // 计算新的高度（最小为原始高度，最大为原始高度的2倍）
                    val newHeight = (height + dy).coerceIn(
                        originalHeight,
                        originalHeight * 2
                    )
                    
                    // 更新布局参数
                    layoutParams.width = newWidth
                    layoutParams.height = newHeight
                    requestLayout()
                    
                    lastX = event.x
                    lastY = event.y
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isResizing = false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
        // 确保光标始终可见
        if (isFocused) {
            val offset = selectionStart
            if (offset != -1) {
                val line = layout.getLineForOffset(offset)
                val x = layout.getPrimaryHorizontal(offset)
                val y = layout.getLineTop(line)
                
                // 如果光标在视图外，滚动到光标位置
                if (x < scrollX || x > scrollX + width - paddingRight - paddingLeft ||
                    y < scrollY || y > scrollY + height - paddingBottom - paddingTop) {
                    scrollTo(x.toInt(), y)
                }
            }
        }
    }
} 