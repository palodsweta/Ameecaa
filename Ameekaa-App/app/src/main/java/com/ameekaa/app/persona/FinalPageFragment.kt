package com.ameekaa.app.persona

import android.content.Intent
import android.view.View
import android.widget.Button
import com.ameekaa.app.MainActivity
import com.ameekaa.app.R

class FinalPageFragment : BasePersonaFragment() {
    
    private lateinit var btnGetStarted: Button

    override fun getLayoutResourceId(): Int = R.layout.fragment_final_page

    override fun setupViews(view: View) {
        btnGetStarted = view.findViewById(R.id.btnGetStarted)
        
        btnGetStarted.setOnClickListener {
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        // TODO: Save onboarding completion status
        // For now, just return to main activity
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    override fun validateSection(): Boolean {
        // No validation needed for final page
        return true
    }

    override fun saveData() {
        // No data to save on final page
    }
} 


