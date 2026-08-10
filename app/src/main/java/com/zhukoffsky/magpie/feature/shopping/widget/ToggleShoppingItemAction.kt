package com.zhukoffsky.magpie.feature.shopping.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.zhukoffsky.magpie.MagpieApp

/**
 * Отметка «куплено» прямо с виджета.
 *
 * Ручной DI здесь окупается: колбэк получает только [Context] и достаёт
 * репозиторий из контейнера в одну строку.
 */
class ToggleShoppingItemAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val id = parameters[ITEM_ID] ?: return
        val wasChecked = parameters[IS_CHECKED] ?: false

        (context.applicationContext as MagpieApp)
            .container
            .shoppingRepository
            .setChecked(id, !wasChecked)
    }

    companion object {
        val ITEM_ID = ActionParameters.Key<Long>("itemId")
        val IS_CHECKED = ActionParameters.Key<Boolean>("isChecked")
    }
}
