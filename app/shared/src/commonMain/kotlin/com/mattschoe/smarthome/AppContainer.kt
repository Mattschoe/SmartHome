package com.mattschoe.smarthome

import com.mattschoe.smarthome.data.HomeAdapter
import com.mattschoe.smarthome.data.MockAdapter

/**
 * Manual DI container. Holds shared dependencies (adapters, repositories) and is constructed in each
 * platform entry point, then passed into `App()`.
 */
class AppContainer(
    val homeAdapter: HomeAdapter = MockAdapter(), // TODO(Phase 9): swap MockAdapter for a HomeAssistantAdapter
)
