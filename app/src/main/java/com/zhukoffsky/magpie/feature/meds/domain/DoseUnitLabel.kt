package com.zhukoffsky.magpie.feature.meds.domain

import androidx.annotation.StringRes
import com.zhukoffsky.magpie.R
import com.zhukoffsky.magpie.core.data.db.DoseUnit

/** Подпись единицы: «мг», «шт». Одна на экран, уведомление и виджет. */
@get:StringRes
val DoseUnit.labelRes: Int
    get() = when (this) {
        DoseUnit.MG -> R.string.med_unit_mg
        DoseUnit.PIECES -> R.string.med_unit_pieces
    }
