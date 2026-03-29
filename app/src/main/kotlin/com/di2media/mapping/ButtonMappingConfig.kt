package com.di2media.mapping

import android.content.Context
import com.di2media.service.PressType
import org.json.JSONObject

data class ButtonBinding(val channel: Int, val pressType: PressType)

class ButtonMappingConfig(context: Context) {

    private val prefs = context.getSharedPreferences("di2_mappings", Context.MODE_PRIVATE)
    private var mappings: MutableMap<ButtonBinding, ButtonAction> = loadMappings()

    fun getAction(channel: Int, pressType: PressType): ButtonAction {
        return mappings[ButtonBinding(channel, pressType)]
            ?: if (pressType == PressType.LONG) HoldAction.NONE else InstantAction.NONE
    }

    fun getInstantAction(channel: Int, pressType: PressType): InstantAction {
        val action = getAction(channel, pressType)
        return action as? InstantAction ?: InstantAction.NONE
    }

    fun getHoldAction(channel: Int): HoldAction {
        val action = getAction(channel, PressType.LONG)
        return action as? HoldAction ?: HoldAction.NONE
    }

    fun getAllMappings(): Map<ButtonBinding, ButtonAction> = mappings.toMap()

    fun setMapping(binding: ButtonBinding, action: ButtonAction) {
        mappings[binding] = action
        saveMappings()
    }

    private fun loadMappings(): MutableMap<ButtonBinding, ButtonAction> {
        val json = prefs.getString("mappings", null)
            ?: return defaultMappings().toMutableMap()

        val result = mutableMapOf<ButtonBinding, ButtonAction>()
        val obj = JSONObject(json)
        for (key in obj.keys()) {
            val parts = key.split(":")
            if (parts.size != 2) continue
            val channel = parts[0].toIntOrNull() ?: continue
            val pressType = PressType.entries.find { it.name == parts[1] } ?: continue
            val value = obj.getString(key)
            val action = parseAction(pressType, value) ?: continue
            result[ButtonBinding(channel, pressType)] = action
        }
        return result
    }

    private fun saveMappings() {
        val obj = JSONObject()
        mappings.forEach { (binding, action) ->
            val key = "${binding.channel}:${binding.pressType.name}"
            val value = when (action) {
                is InstantAction -> "instant:${action.name}"
                is HoldAction -> "hold:${action.name}"
            }
            obj.put(key, value)
        }
        prefs.edit().putString("mappings", obj.toString()).apply()
    }

    companion object {
        fun defaultMappings(): Map<ButtonBinding, ButtonAction> = mapOf(
            ButtonBinding(1, PressType.SHORT) to InstantAction.PREVIOUS,
            ButtonBinding(1, PressType.LONG) to HoldAction.NONE,
            ButtonBinding(1, PressType.DOUBLE) to InstantAction.PLAY_PAUSE,
            ButtonBinding(2, PressType.SHORT) to InstantAction.NEXT,
            ButtonBinding(2, PressType.LONG) to HoldAction.NONE,
            ButtonBinding(2, PressType.DOUBLE) to InstantAction.NONE,
        )

        private fun parseAction(pressType: PressType, value: String): ButtonAction? {
            val parts = value.split(":")
            if (parts.size != 2) return null
            return when (parts[0]) {
                "instant" -> InstantAction.entries.find { it.name == parts[1] }
                "hold" -> HoldAction.entries.find { it.name == parts[1] }
                else -> null
            }
        }
    }
}
