package com.studymate.sm.cid

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.studymate.sm.cid.data.AppDatabase
import com.studymate.sm.cid.repository.StudyRepository
import com.studymate.sm.cid.util.PreferencesManager

class StudyMateApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: StudyRepository by lazy { StudyRepository.getInstance(database) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        terapkanModeTema()
    }

    private fun terapkanModeTema() {
        val prefs = PreferencesManager(this)
        val mode = if (prefs.temaGelap) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    companion object {
        lateinit var instance: StudyMateApp
            private set
    }
}
