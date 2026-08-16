package com.interactiveplayer.app

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView

/** Ícones das mesmas fontes usadas por @expo/vector-icons no aplicativo original. */
object OriginalIcons {
    private val cache = HashMap<String, Typeface>()

    fun apply(textView: TextView, name: String) {
        val icon = icons[name] ?: return
        textView.typeface = typeface(textView.context, icon.family)
        textView.text = String(Character.toChars(icon.codePoint))
    }

    private fun typeface(context: Context, family: Family): Typeface {
        val asset = when (family) {
            Family.IONICONS -> "original_media/icon_fonts/Ionicons.ttf"
            Family.MATERIAL_COMMUNITY -> "original_media/icon_fonts/MaterialCommunityIcons.ttf"
            Family.MATERIAL -> "original_media/icon_fonts/MaterialIcons.ttf"
        }
        return cache.getOrPut(asset) { Typeface.createFromAsset(context.assets, asset) }
    }

    enum class Family { IONICONS, MATERIAL_COMMUNITY, MATERIAL }
    private data class Icon(val family: Family, val codePoint: Int)

    private val icons = mapOf(
        "home" to Icon(Family.IONICONS, 62338),
        "tv" to Icon(Family.IONICONS, 62980),
        "film" to Icon(Family.IONICONS, 62206),
        "series" to Icon(Family.MATERIAL_COMMUNITY, 985076),
        "trophy" to Icon(Family.MATERIAL_COMMUNITY, 984376),
        "kids" to Icon(Family.MATERIAL_COMMUNITY, 986749),
        "radio" to Icon(Family.MATERIAL_COMMUNITY, 984121),
        "camera" to Icon(Family.IONICONS, 61915),
        "search" to Icon(Family.IONICONS, 62815),
        "settings" to Icon(Family.IONICONS, 62827),
        "diagnostic" to Icon(Family.MATERIAL_COMMUNITY, 988493),
        "play" to Icon(Family.IONICONS, 62662),
        "heart" to Icon(Family.IONICONS, 62314),
        "back" to Icon(Family.IONICONS, 61735),
        "close" to Icon(Family.IONICONS, 62026),
        "add" to Icon(Family.IONICONS, 61699),
    )
}
