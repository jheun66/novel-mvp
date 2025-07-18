package com.novel.di

import com.novel.database.DatabaseFactory
import org.koin.dsl.module

val databaseModule = module {
    single<DatabaseFactory> {
        DatabaseFactory(get(), get())
    }
}
