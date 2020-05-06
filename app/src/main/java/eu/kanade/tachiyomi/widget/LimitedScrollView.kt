package eu.kanade.tachiyomi.widget

import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View.MeasureSpec
import android.widget.ScrollView
import eu.kanade.tachiyomi.R

class LimitedScrollView(context: Context, attrs: AttributeSet) : ScrollView(context, attrs) {

    var maxHeight: Int

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.LimitedScrollView)
        maxHeight = a.getInt(R.styleable.LimitedScrollView_max_height, 100)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dpToPx(getResources(), maxHeight), MeasureSpec.AT_MOST))
    }

    private fun dpToPx(res: Resources, dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), res.getDisplayMetrics()).toInt()
    }
}
