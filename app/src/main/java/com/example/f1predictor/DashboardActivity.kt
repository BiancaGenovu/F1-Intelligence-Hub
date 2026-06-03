package com.example.f1predictor

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.f1predictor.databinding.ActivityDashboardBinding
import com.google.firebase.auth.FirebaseAuth

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        val currentUser = auth.currentUser
        binding.tvWelcome.text = "BINE AI VENIT, PILOT (${currentUser?.email?.split("@")?.get(0)?.uppercase()})"

        // Deschidere Profil propriu
        binding.btnProfile.setOnClickListener {
            val intent = Intent(this, ProfileSetupActivity::class.java)
            intent.putExtra("IS_EDIT_MODE", true)
            startActivity(intent)
        }

        // Deschidere Comunitate Piloți (ADĂUGAT)
        binding.btnCommunity.setOnClickListener {
            val intent = Intent(this, CommunityDriversActivity::class.java)
            startActivity(intent)
        }

        // Listenere predicții și minijocuri
        binding.cardSinglePilot.setOnClickListener {
            startActivity(Intent(this, SinglePredictionActivity::class.java))
        }

        binding.cardFullRace.setOnClickListener {
            startActivity(Intent(this, RaceSimulationActivity::class.java))
        }

        binding.cardChampionship.setOnClickListener {
            startActivity(Intent(this, ChampionshipActivity::class.java))
        }

        binding.cardDuel.setOnClickListener {
            startActivity(Intent(this, DuelActivity::class.java))
        }

        binding.cardMiniGame.setOnClickListener {
            startActivity(Intent(this, MinigamesMenuActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}