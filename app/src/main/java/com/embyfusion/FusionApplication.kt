package com.embyfusion

import android.app.Application
import com.embyfusion.data.EmbyRepository
import com.embyfusion.data.local.ServerStore
import com.embyfusion.data.remote.EmbyApiClient

class FusionApplication : Application() {
    val repository by lazy {
        EmbyRepository(
            store = ServerStore(this),
            api = EmbyApiClient()
        )
    }
}

