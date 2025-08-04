package com.ameekaa.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.CheckBox
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.ameekaa.app.persona.PersonaCreationActivity

class CompanionIntroActivity : AppCompatActivity() {

    private lateinit var checkboxUnderstand: CheckBox
    private lateinit var btnContinue: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_companion_intro)

        // Set up toolbar with back button
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Welcome to Amica"
        }

        checkboxUnderstand = findViewById(R.id.checkboxUnderstand)
        btnContinue = findViewById(R.id.btnContinue)

        setupContinueButton()
        setupBackHandling()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.menu_mvp_test -> {
                startActivity(Intent(this, DiarizationTest::class.java))
                true
            }
            R.id.action_onboarding -> {
                startActivity(Intent(this, AmeekaaOnboarding::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupContinueButton() {
        btnContinue.setOnClickListener {
            if (checkboxUnderstand.isChecked) {
                startActivity(Intent(this, PersonaCreationActivity::class.java))
            }
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
} 


