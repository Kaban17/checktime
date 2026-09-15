package dev.boar.checktime

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.boar.checktime.data.AppDatabase

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
