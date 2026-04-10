package io.github.martinezanthony.sqliteviewer.utils

import android.content.Context
import android.util.TypedValue

fun Context.dpToPx(dp: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

fun Context.dpToPxInt(dp: Float): Int = dpToPx(dp).toInt()