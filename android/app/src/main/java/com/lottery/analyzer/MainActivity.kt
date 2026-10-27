package com.lottery.analyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.lottery.analyzer.ui.screens.MainContainerScreen
import com.lottery.analyzer.ui.theme.LotteryMasterTheme
import com.lottery.analyzer.ui.viewmodel.LotteryViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LotteryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LotteryMasterTheme {
                MainContainerScreen(viewModel = viewModel)
            }
        }
    }
}
