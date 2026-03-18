package com.mikepenz.agentapprover.hook

import co.touchlab.kermit.Logger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File

private val logger = Logger.withTag("HookRegistrar")

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

@Serializable
private data class HookEntry(
    val matcher: String = "",
    val hooks: List<HookDef> = emptyList(),
)

@Serializable
private data class HookDef(
    val type: String,
    val url: String,
)

object HookRegistrar {

    private fun settingsFile(): File {
        val home = System.getProperty("user.home")
        return File(home, ".claude/settings.json")
    }

    private fun hookUrl(port: Int): String = "http://localhost:$port/approve"

    fun isRegistered(port: Int): Boolean {
        val file = settingsFile()
        if (!file.exists()) return false
        return try {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            val hooks = root["hooks"]?.jsonObject ?: return false
            val permEntries = hooks["PermissionRequest"]?.jsonArray ?: return false
            val url = hookUrl(port)
            permEntries.any { entry ->
                val obj = entry.jsonObject
                val innerHooks = obj["hooks"]?.jsonArray ?: return@any false
                innerHooks.any { h ->
                    val hObj = h.jsonObject
                    hObj["type"].toString().trim('"') == "http" &&
                        hObj["url"].toString().trim('"') == url
                }
            }
        } catch (e: Exception) {
            logger.w(e) { "Failed to read settings.json" }
            false
        }
    }

    fun register(port: Int) {
        val file = settingsFile()
        val url = hookUrl(port)

        if (isRegistered(port)) {
            logger.i { "Hook already registered for port $port" }
            return
        }

        val root: JsonObject = if (file.exists()) {
            try {
                json.parseToJsonElement(file.readText()).jsonObject
            } catch (e: Exception) {
                logger.w(e) { "Failed to parse settings.json, starting fresh" }
                JsonObject(emptyMap())
            }
        } else {
            JsonObject(emptyMap())
        }

        val newEntry = json.encodeToJsonElement(
            HookEntry(
                matcher = "",
                hooks = listOf(HookDef(type = "http", url = url)),
            )
        )

        val existingHooks = root["hooks"]?.jsonObject ?: JsonObject(emptyMap())
        val existingPerm = existingHooks["PermissionRequest"]?.jsonArray?.toMutableList() ?: mutableListOf()
        existingPerm.add(newEntry)

        val updatedHooks = buildJsonObject {
            existingHooks.forEach { (key, value) ->
                if (key != "PermissionRequest") put(key, value)
            }
            put("PermissionRequest", Json.encodeToJsonElement(existingPerm))
        }

        val updatedRoot = buildJsonObject {
            root.forEach { (key, value) ->
                if (key != "hooks") put(key, value)
            }
            put("hooks", updatedHooks)
        }

        file.parentFile.mkdirs()
        file.writeText(json.encodeToString(JsonElement.serializer(), updatedRoot))
        logger.i { "Registered hook for port $port" }
    }

    fun unregister(port: Int) {
        val file = settingsFile()
        if (!file.exists()) return

        val root: JsonObject = try {
            json.parseToJsonElement(file.readText()).jsonObject
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse settings.json" }
            return
        }

        val existingHooks = root["hooks"]?.jsonObject ?: return
        val existingPerm = existingHooks["PermissionRequest"]?.jsonArray ?: return
        val url = hookUrl(port)

        val filtered = existingPerm.filter { entry ->
            val obj = entry.jsonObject
            val innerHooks = obj["hooks"]?.jsonArray ?: return@filter true
            val hasOurHook = innerHooks.any { h ->
                val hObj = h.jsonObject
                hObj["type"].toString().trim('"') == "http" &&
                    hObj["url"].toString().trim('"') == url
            }
            !hasOurHook
        }

        val updatedHooks = buildJsonObject {
            existingHooks.forEach { (key, value) ->
                if (key != "PermissionRequest") put(key, value)
            }
            if (filtered.isNotEmpty()) {
                put("PermissionRequest", Json.encodeToJsonElement(filtered))
            }
        }

        val updatedRoot = buildJsonObject {
            root.forEach { (key, value) ->
                if (key != "hooks") put(key, value)
            }
            if (updatedHooks.isNotEmpty()) {
                put("hooks", updatedHooks)
            }
        }

        file.writeText(json.encodeToString(JsonElement.serializer(), updatedRoot))
        logger.i { "Unregistered hook for port $port" }
    }
}
