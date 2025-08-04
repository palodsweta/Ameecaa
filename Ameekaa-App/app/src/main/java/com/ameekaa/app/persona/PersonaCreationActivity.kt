package com.ameekaa.app.persona

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.viewpager2.widget.ViewPager2
import com.ameekaa.app.R
import com.ameekaa.app.DiarizationTest
import com.ameekaa.app.AmeekaaOnboarding
import com.google.android.material.progressindicator.LinearProgressIndicator

class PersonaCreationActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var btnNext: Button
    private lateinit var btnPrevious: Button
    private lateinit var sectionTitle: TextView
    private lateinit var pagerAdapter: PersonaSectionsPagerAdapter

    private val sections = listOf(
        "Our Starting Point",
        "What Feels Most Like You?",
        "Joy Toolkit",
        "The Activity Toolkit",
        "The Connection Toolkit",
        "The Mind Toolkit",
        "The Conversations in Your Life",
        "Your Trusted Circle",
        "Your Safety",
        "Final Page"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_persona_creation)

        // Set up toolbar with back button
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Creating Your Profile"
        }

        initializeViews()
        setupViewPager()
        setupButtons()
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

    private fun initializeViews() {
        viewPager = findViewById(R.id.viewPager)
        progressIndicator = findViewById(R.id.progressIndicator)
        btnNext = findViewById(R.id.btnNext)
        btnPrevious = findViewById(R.id.btnPrevious)
        sectionTitle = findViewById(R.id.sectionTitle)

        progressIndicator.max = (sections.size - 1) * 100
    }

    private fun setupViewPager() {
        pagerAdapter = PersonaSectionsPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        viewPager.isUserInputEnabled = false // Disable swiping

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUI(position)
            }
        })
    }

    private fun setupButtons() {
        btnNext.setOnClickListener {
            if (viewPager.currentItem < sections.size - 1) {
                viewPager.currentItem = viewPager.currentItem + 1
            }
        }

        btnPrevious.setOnClickListener {
            if (viewPager.currentItem > 0) {
                viewPager.currentItem = viewPager.currentItem - 1
            }
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewPager.currentItem > 0) {
                    viewPager.currentItem = viewPager.currentItem - 1
                } else {
                    finish()
                }
            }
        })
    }

    private fun updateUI(position: Int) {
        // Update progress
        progressIndicator.progress = position * 100

        // Update section title
        sectionTitle.text = sections[position]

        // Update button visibility
        btnPrevious.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
        btnNext.text = if (position == sections.size - 1) "Finish" else "Next"
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
} 


