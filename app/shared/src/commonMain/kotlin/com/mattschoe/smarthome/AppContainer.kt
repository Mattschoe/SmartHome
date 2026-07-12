package com.mattschoe.smarthome

import com.mattschoe.smarthome.data.HaConfig
import com.mattschoe.smarthome.data.HomeAdapter
import com.mattschoe.smarthome.data.HomeAssistantAdapter
import com.mattschoe.smarthome.data.MockAdapter

/**
 * Manual DI container. Holds shared dependencies (adapters, repositories) and is constructed in each
 * platform entry point, then passed into `App()`.
 *
 * The [homeAdapter] is chosen from [haConfig]: a live [HomeAssistantAdapter] when a config with a
 * non-blank token is supplied, otherwise the in-memory [MockAdapter] (the default, and what previews use).
 */
class AppContainer(
    haConfig: HaConfig? = haConfigFromSecrets(),
    val homeAdapter: HomeAdapter = if (haConfig?.hasToken == true) HomeAssistantAdapter(haConfig) else MockAdapter(),
)

/**
 * Builds an [HaConfig] from the code-generated [BuildSecrets] (sourced from repo-root `local.properties`
 * by the `:shared` `generateBuildSecrets` Gradle task), shared by every platform entry point. Returns
 * `null` when no token is configured, so [AppContainer] falls back to the [MockAdapter].
 */
fun haConfigFromSecrets(): HaConfig? =
    BuildSecrets.HA_TOKEN
        .takeIf { it.isNotBlank() }
        ?.let { HaConfig(url = BuildSecrets.HA_URL, token = it) }
