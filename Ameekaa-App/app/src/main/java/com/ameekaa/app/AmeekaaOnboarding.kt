package com.ameekaa.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.activity.OnBackPressedCallback

class AmeekaaOnboarding : AppCompatActivity() {
    private lateinit var optionsGroup: RadioGroup
    private lateinit var btnContinue: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        // Set up toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        optionsGroup = findViewById(R.id.optionsGroup)
        btnContinue = findViewById(R.id.btnContinue)

        // Enable only the first option by default
        for (i in 1 until optionsGroup.childCount) {
            optionsGroup.getChildAt(i).isEnabled = false
        }

        btnContinue.isEnabled = false

        optionsGroup.setOnCheckedChangeListener { _, checkedId ->
            btnContinue.isEnabled = checkedId != -1
        }

        btnContinue.setOnClickListener {
            val intent = Intent(this, CompanionIntroActivity::class.java)
            startActivity(intent)
        }

        setupBackHandling()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_mvp_test -> {
                startActivity(Intent(this, DiarizationTest::class.java))
                true
            }
            R.id.action_onboarding -> {
                // Already in onboarding
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }
} 


