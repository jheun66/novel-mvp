package com.novel.di

import org.koin.dsl.module
import java.util.concurrent.ConcurrentHashMap // Important for thread-safety

val authModule = module {
    // Defines a single instance of ConcurrentHashMap for storing OAuth redirect URLs.
    // The key will be the 'state' parameter from OAuth, and the value will be the redirect URL.
    single { ConcurrentHashMap<String, String>() }
}