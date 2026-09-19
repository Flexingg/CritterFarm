package com.critterfarm

import android.app.Application
import com.critterfarm.data.GameRepository
import com.critterfarm.data.OnboardingPreferences
import com.critterfarm.data.local.CritterFarmDatabase
import com.critterfarm.health.HealthConnectManager

class CritterFarmApplication : Application() {

    val database: CritterFarmDatabase by lazy { CritterFarmDatabase.getInstance(this) }

    val gameRepository: GameRepository by lazy {
        GameRepository(
            critterDao = database.critterDao(),
            inventoryDao = database.farmInventoryDao(),
            dailySummaryLogDao = database.dailySummaryLogDao(),
            questClaimDao = database.questClaimDao(),
        )
    }

    val healthConnectManager: HealthConnectManager by lazy { HealthConnectManager(this) }

    val onboardingPreferences: OnboardingPreferences by lazy { OnboardingPreferences(this) }
}
