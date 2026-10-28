package com.example.chat.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedAccount(
    val email: String,
    val displayName: String,
    val photoUrl: String
)

object SavedAccountManager {
    private const val PREF_NAME = "chatly_saved_accounts"
    private const val KEY_ACCOUNTS = "accounts"

    fun getSavedAccounts(context: Context): List<SavedAccount> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_ACCOUNTS, "[]") ?: "[]"
        val list = mutableListOf<SavedAccount>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    SavedAccount(
                        email = obj.getString("email"),
                        displayName = obj.getString("displayName"),
                        photoUrl = obj.optString("photoUrl", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveAccount(context: Context, email: String, displayName: String, photoUrl: String) {
        val accounts = getSavedAccounts(context).toMutableList()
        accounts.removeAll { it.email.equals(email, ignoreCase = true) }
        accounts.add(0, SavedAccount(email, displayName, photoUrl))

        val jsonArray = JSONArray()
        for (acc in accounts) {
            val obj = JSONObject().apply {
                put("email", acc.email)
                put("displayName", acc.displayName)
                put("photoUrl", acc.photoUrl)
            }
            jsonArray.put(obj)
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACCOUNTS, jsonArray.toString()).apply()
    }
}
