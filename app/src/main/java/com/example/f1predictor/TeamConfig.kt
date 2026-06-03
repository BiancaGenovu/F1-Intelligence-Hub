package com.example.f1predictor

import androidx.compose.ui.graphics.Color

data class TeamConfig(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val secondaryColor: Color = Color(0xFF121212) // Negru carbon/închis implicit pentru gradient
)

object TeamProvider {
    val teams = listOf(
        TeamConfig(id = "1", name = "McLaren", primaryColor = Color(0xFFFF8000)),
        TeamConfig(id = "2", name = "Red Bull", primaryColor = Color(0xFF0600EF)),
        TeamConfig(id = "3", name = "Mercedes", primaryColor = Color(0xFF00D2BE)),
        TeamConfig(id = "4", name = "Ferrari", primaryColor = Color(0xFFDC0000)),
        TeamConfig(id = "5", name = "Williams", primaryColor = Color(0xFF005AFF)),
        TeamConfig(id = "6", name = "RB", primaryColor = Color(0xFF6692FF)),
        TeamConfig(id = "7", name = "Kick Sauber", primaryColor = Color(0xFF52E252)),
        TeamConfig(id = "8", name = "Aston Martin", primaryColor = Color(0xFF006F62)),
        TeamConfig(id = "9", name = "Haas", primaryColor = Color(0xFFFFFFFF), secondaryColor = Color(0xFF333333)), // Fundal gri mai închis pentru Haas ca să se vadă textul alb
        TeamConfig(id = "10", name = "Alpine", primaryColor = Color(0xFFFF87BC))
    )
}