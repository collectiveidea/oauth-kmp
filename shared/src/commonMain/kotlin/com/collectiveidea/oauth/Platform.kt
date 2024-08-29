package com.collectiveidea.oauth

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform