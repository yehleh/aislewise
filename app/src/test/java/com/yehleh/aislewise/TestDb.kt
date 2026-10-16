package com.yehleh.aislewise

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yehleh.aislewise.data.db.AppDatabase
import com.yehleh.aislewise.data.db.SeedCallback

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .addCallback(SeedCallback)
        .allowMainThreadQueries()
        .build()
