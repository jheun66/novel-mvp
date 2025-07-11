package com.novel.di

import com.novel.config.DBConfig
import com.novel.config.JWTConfig
import com.novel.config.OAuthConfig
import org.koin.dsl.module

val configModule = module {
    single<JWTConfig> {
        JWTConfig.fromApplication(get())
    }

    single<DBConfig> {
        DBConfig.fromApplication(get())
    }

    single<OAuthConfig> {
        OAuthConfig.fromApplication(get())
    }
}