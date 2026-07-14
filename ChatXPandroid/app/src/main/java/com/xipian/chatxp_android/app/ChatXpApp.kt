package com.xipian.chatxp_android.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xipian.chatxp_android.ui.screens.chat.ChatRoute
import com.xipian.chatxp_android.ui.theme.ChatXPandroidTheme

@Composable
fun ChatXpApp() {
    ChatXPandroidTheme {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            ChatRoute(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}
