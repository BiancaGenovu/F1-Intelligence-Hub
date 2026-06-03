package com.example.f1predictor

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.f1predictor.databinding.ActivityMinigamesMenuBinding

class MinigamesMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMinigamesMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMinigamesMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.cardCircuitQuiz.setOnClickListener {
            startActivity(Intent(this, CircuitQuizActivity::class.java))
        }

        binding.cardReactionGame.setOnClickListener {
            startActivity(Intent(this, ReactionGameActivity::class.java))
        }

        binding.cardPitStopGame.setOnClickListener {
            startActivity(Intent(this, PitStopGameActivity::class.java))
        }

        binding.cardSteeringGame.setOnClickListener {
            startActivity(Intent(this, SteeringGameActivity::class.java))
        }
    }
}