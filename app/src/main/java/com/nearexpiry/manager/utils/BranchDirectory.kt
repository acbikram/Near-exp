package com.nearexpiry.manager.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Branch directory bundled from the company's Near-Expiry template (Sheet3:
 * ID → STORE_NAME, Area). Used by the company report export to fill the
 * Area (col B) and Branch Name (col D) from the Branch ID the user types,
 * so the output matches the template without external VLOOKUPs.
 */
@Singleton
class BranchDirectory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Branch(val id: String, val name: String, val area: String)

    private val branches: Map<String, Branch> by lazy { load() }

    private fun load(): Map<String, Branch> {
        return try {
            val text = context.assets.open("branches.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(text)
            val map = HashMap<String, Branch>(obj.length())
            obj.keys().forEach { key ->
                val b = obj.getJSONObject(key)
                map[key.trim()] = Branch(
                    id = key.trim(),
                    name = b.optString("name", ""),
                    area = b.optString("area", "")
                )
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Returns the branch for [id], or null if the ID isn't in the directory. */
    fun lookup(id: String): Branch? = branches[id.trim()]
}
