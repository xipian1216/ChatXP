package com.xipian.chatxp_android.app

import android.app.Application

class ChatXpApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
