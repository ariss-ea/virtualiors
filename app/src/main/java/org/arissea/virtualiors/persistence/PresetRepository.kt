package org.arissea.virtualiors.persistence

import android.content.Context
import org.arissea.virtualiors.model.AppConfig
import org.arissea.virtualiors.model.AprsConfig
import org.arissea.virtualiors.model.AprsNoGpsLockBehavior
import org.arissea.virtualiors.model.AprsPositionSource
import org.arissea.virtualiors.model.BuiltInPresets
import org.arissea.virtualiors.model.CURRENT_SCHEMA_VERSION
import org.arissea.virtualiors.model.CameraFacing
import org.arissea.virtualiors.model.MediaSource
import org.arissea.virtualiors.model.Preset
import org.arissea.virtualiors.model.SstvModeOption
import org.arissea.virtualiors.model.SstvSourceType
import org.arissea.virtualiors.model.VoiceConfig
import org.arissea.virtualiors.model.WatermarkConfig
import org.json.JSONArray
import org.json.JSONObject

data class LegacyPresetData(
    val name: String,
    val audioUris: List<String>,
    val useExternalTtsAudio: Boolean,
    val ttsAudioUri: String?,
    val speakEvery: Int?,
    val cooldownSeconds: Int,
    val shuffle: Boolean,
    val ttsPhrase: String?,
    val voxTone: Boolean,
)

object PresetMigration {
    fun v1ToV2(old: LegacyPresetData): Preset = Preset(
        name = old.name,
        config = AppConfig(
            sourceType = SstvSourceType.AUDIO_FILES,
            audioSources = old.audioUris.mapIndexed { index, uri ->
                MediaSource(uri = uri, displayName = uri.substringAfterLast('/').substringAfterLast(':').ifBlank { "Audio ${index + 1}" })
            },
            voice = VoiceConfig(
                enabled = old.speakEvery != null,
                everyImages = old.speakEvery ?: 3,
                useExternalAudio = old.useExternalTtsAudio,
                externalAudioUri = old.ttsAudioUri,
                phrase = old.ttsPhrase ?: VoiceConfig().phrase,
            ),
            aprs = AprsConfig(enabled = false),
            cooldownSeconds = old.cooldownSeconds,
            shuffle = old.shuffle,
            globalVoxTone = old.voxTone,
        ).normalized(),
    )
}

class PresetRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadCurrentConfig(): AppConfig {
        val raw = prefs.getString(KEY_CURRENT_CONFIG, null) ?: return AppConfig()
        return runCatching {
            val config = JSONObject(raw)
            if (config.optInt("schemaVersion", CURRENT_SCHEMA_VERSION) < 2) {
                AppConfig()
            } else {
                parseV2(JSONObject().put("name", "Current configuration"), config).config
            }
        }.getOrElse { AppConfig() }
    }

    fun saveCurrentConfig(config: AppConfig) {
        val serialized = toJson(Preset("Current configuration", config))
            .getJSONObject("config")
            .toString()
        prefs.edit().putString(KEY_CURRENT_CONFIG, serialized).apply()
    }

    fun load(): List<Preset> {
        val raw = prefs.getString(KEY_PRESETS, "[]") ?: "[]"
        var migrated = false
        val custom = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val objectValue = array.optJSONObject(index) ?: continue
                    val configObject = objectValue.optJSONObject("config") ?: continue
                    val preset = runCatching {
                        if (configObject.optInt("schemaVersion", 1) < CURRENT_SCHEMA_VERSION) {
                            val migratedPreset = parseV1(objectValue, configObject)
                            migrated = true
                            migratedPreset
                        } else {
                            parseV2(objectValue, configObject)
                        }
                    }.getOrNull() ?: continue
                    if (preset.name !in BuiltInPresets.names) add(preset)
                }
            }
        }.getOrElse { emptyList() }

        if (migrated) saveCustom(custom)
        return BuiltInPresets.all() + custom
    }

    fun upsert(name: String, config: AppConfig) {
        val safeName = name.trim()
        require(safeName.isNotBlank()) { "Preset name cannot be blank" }
        require(safeName !in BuiltInPresets.names) { "Built-in presets cannot be overwritten" }
        val custom = load().filterNot { it.builtIn || it.name == safeName }.toMutableList()
        custom += Preset(safeName, config.normalized())
        saveCustom(custom)
    }

    fun delete(preset: Preset) {
        if (preset.builtIn) return
        saveCustom(load().filterNot { it.builtIn || it.name == preset.name })
    }

    private fun saveCustom(presets: List<Preset>) {
        val array = JSONArray()
        presets.filterNot { it.builtIn }.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_PRESETS, array.toString()).apply()
    }

    private fun parseV1(wrapper: JSONObject, config: JSONObject): Preset {
        val audio = config.optJSONArray("audio")?.strings().orEmpty()
        return PresetMigration.v1ToV2(
            LegacyPresetData(
                name = wrapper.optString("name", "Migrated preset"),
                audioUris = audio,
                useExternalTtsAudio = config.optBoolean("ttsExt", false),
                ttsAudioUri = config.nullableString("ttsUri"),
                speakEvery = config.nullableInt("speakEvery"),
                cooldownSeconds = config.optInt("cooldown", 120),
                shuffle = config.optBoolean("shuffle", false),
                ttsPhrase = config.nullableString("ttsPhrase"),
                voxTone = config.optBoolean("voxTone", false),
            ),
        )
    }

    private fun parseV2(wrapper: JSONObject, config: JSONObject): Preset {
        val voice = config.optJSONObject("voice") ?: JSONObject()
        val aprs = config.optJSONObject("aprs") ?: JSONObject()
        val watermark = config.optJSONObject("watermark") ?: JSONObject()
        return Preset(
            name = wrapper.optString("name", "Preset"),
            config = AppConfig(
                sourceType = config.enumValue("sourceType", SstvSourceType.AUDIO_FILES),
                audioSources = config.optJSONArray("audioSources")?.mediaSources().orEmpty(),
                imageSources = config.optJSONArray("imageSources")?.mediaSources().orEmpty(),
                sstvMode = config.enumValue("sstvMode", SstvModeOption.ROBOT36),
                cameraFacing = config.enumValue("cameraFacing", CameraFacing.FRONT),
                watermark = WatermarkConfig(
                    enabled = watermark.optBoolean("enabled", false),
                    showCallsign = watermark.optBoolean("showCallsign", true),
                    showImageNumber = watermark.optBoolean("showImageNumber", true),
                    showTimestamp = watermark.optBoolean("showTimestamp", true),
                ),
                voice = VoiceConfig(
                    enabled = voice.optBoolean("enabled", false),
                    everyImages = voice.optInt("everyImages", 3),
                    useExternalAudio = voice.optBoolean("useExternalAudio", false),
                    externalAudioUri = voice.nullableString("externalAudioUri"),
                    phrase = voice.optString("phrase", VoiceConfig().phrase),
                ),
                aprs = AprsConfig(
                    enabled = aprs.optBoolean("enabled", false),
                    everyImages = aprs.optInt("everyImages", 3),
                    packetSpacingSeconds = aprs.optInt("packetSpacingSeconds", 5),
                    beaconEnabled = true,
                    gpsPositionEnabled = aprs.optBoolean("gpsPositionEnabled", false),
                    positionSource = aprs.enumValue("positionSource", AprsPositionSource.PHONE_GPS),
                    noGpsLockBehavior = aprs.enumValue(
                        "noGpsLockBehavior",
                        AprsNoGpsLockBehavior.SEND_NO_GPS_LOCK,
                    ),
                    presetLatitude = aprs.optString("presetLatitude", ""),
                    presetLongitude = aprs.optString("presetLongitude", ""),
                    telemetryEnabled = aprs.optBoolean("telemetryEnabled", false),
                    customTextEnabled = aprs.optBoolean("customTextEnabled", false),
                    customText = aprs.optString("customText", AprsConfig().customText),
                ),
                cooldownSeconds = config.optInt("cooldownSeconds", 120),
                shuffle = config.optBoolean("shuffle", false),
                globalVoxTone = config.optBoolean("globalVoxTone", false),
                callsign = config.optString("callsign", AppConfig().callsign),
                aprsDestination = config.optString("aprsDestination", AppConfig().aprsDestination),
                aprsPath = config.optString("aprsPath", ""),
            ).normalized(),
        )
    }

    private fun toJson(preset: Preset): JSONObject {
        val config = preset.config.normalized()
        return JSONObject().apply {
            put("name", preset.name)
            put("config", JSONObject().apply {
                put("schemaVersion", CURRENT_SCHEMA_VERSION)
                put("sourceType", config.sourceType.name)
                put("audioSources", config.audioSources.toJson())
                put("imageSources", config.imageSources.toJson())
                put("sstvMode", config.sstvMode.name)
                put("cameraFacing", config.cameraFacing.name)
                put("watermark", JSONObject().apply {
                    put("enabled", config.watermark.enabled)
                    put("showCallsign", config.watermark.showCallsign)
                    put("showImageNumber", config.watermark.showImageNumber)
                    put("showTimestamp", config.watermark.showTimestamp)
                })
                put("voice", JSONObject().apply {
                    put("enabled", config.voice.enabled)
                    put("everyImages", config.voice.everyImages)
                    put("useExternalAudio", config.voice.useExternalAudio)
                    put("externalAudioUri", config.voice.externalAudioUri ?: JSONObject.NULL)
                    put("phrase", config.voice.phrase)
                })
                put("aprs", JSONObject().apply {
                    put("enabled", config.aprs.enabled)
                    put("everyImages", config.aprs.everyImages)
                    put("packetSpacingSeconds", config.aprs.packetSpacingSeconds)
                    put("beaconEnabled", true)
                    put("gpsPositionEnabled", config.aprs.gpsPositionEnabled)
                    put("positionSource", config.aprs.positionSource.name)
                    put("noGpsLockBehavior", config.aprs.noGpsLockBehavior.name)
                    put("presetLatitude", config.aprs.presetLatitude)
                    put("presetLongitude", config.aprs.presetLongitude)
                    put("telemetryEnabled", config.aprs.telemetryEnabled)
                    put("customTextEnabled", config.aprs.customTextEnabled)
                    put("customText", config.aprs.customText)
                })
                put("cooldownSeconds", config.cooldownSeconds)
                put("shuffle", config.shuffle)
                put("globalVoxTone", config.globalVoxTone)
                put("callsign", config.normalizedCallsign)
                put("aprsDestination", config.normalizedDestination)
                put("aprsPath", config.aprsPath)
            })
        }
    }

    private fun JSONArray.strings(): List<String> = buildList {
        for (index in 0 until length()) optString(index).takeIf { it.isNotBlank() }?.let(::add)
    }

    private fun JSONArray.mediaSources(): List<MediaSource> = buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val uri = item.nullableString("uri")
            val resource = item.nullableString("resourceName")
            if (uri == null && resource == null) continue
            add(MediaSource(uri, resource, item.optString("displayName", "Media ${index + 1}")))
        }
    }

    private fun List<MediaSource>.toJson(): JSONArray = JSONArray().also { array ->
        forEach { source ->
            array.put(JSONObject().apply {
                put("uri", source.uri ?: JSONObject.NULL)
                put("resourceName", source.resourceName ?: JSONObject.NULL)
                put("displayName", source.displayName)
            })
        }
    }

    private inline fun <reified T : Enum<T>> JSONObject.enumValue(key: String, fallback: T): T =
        runCatching { enumValueOf<T>(optString(key, fallback.name)) }.getOrDefault(fallback)

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.nullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    companion object {
        private const val PREFS_NAME = "virtual_iors_prefs"
        private const val KEY_PRESETS = "presets_json"
        private const val KEY_CURRENT_CONFIG = "current_config_json"
    }
}
