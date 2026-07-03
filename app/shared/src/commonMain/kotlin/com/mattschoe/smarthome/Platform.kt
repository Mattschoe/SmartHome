package com.mattschoe.smarthome

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
