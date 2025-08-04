package com.ameekaa.app.persona

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ameekaa.app.R

class SafetyFragment : BasePersonaFragment() {
    
    private lateinit var radioGroup1: RadioGroup
    private lateinit var radioGroup2: RadioGroup
    private lateinit var radioGroup3: RadioGroup
    private lateinit var radioGroup4: RadioGroup
    private lateinit var btnCall988: Button

    private var urgentSupportDialog: AlertDialog? = null

    override fun getLayoutResourceId(): Int = R.layout.fragment_safety

    override fun setupViews(view: View) {
        radioGroup1 = view.findViewById(R.id.radioGroup1)
        radioGroup2 = view.findViewById(R.id.radioGroup2)
        radioGroup3 = view.findViewById(R.id.radioGroup3)
        radioGroup4 = view.findViewById(R.id.radioGroup4)
        btnCall988 = view.findViewById(R.id.btnCall988)

        setupCrisisButton()
        setupResponseListeners()
    }

    private fun setupCrisisButton() {
        btnCall988.setOnClickListener {
            showEmergencyCallDialog()
        }
    }

    private fun setupResponseListeners() {
        val radioGroups = listOf(radioGroup1, radioGroup2, radioGroup3, radioGroup4)
        
        radioGroups.forEach { group ->
            group.setOnCheckedChangeListener { _, checkedId ->
                // Check for high-risk responses
                when (checkedId) {
                    R.id.q1_always,
                    R.id.q2_always,
                    R.id.q3_yes_thinking,
                    R.id.q3_yes_unsure,
                    R.id.q4_steps,
                    R.id.q4_attempted -> {
                        showUrgentSupportDialog()
                    }
                }
            }
        }
    }

    private fun showUrgentSupportDialog() {
        // Dismiss any existing dialog
        urgentSupportDialog?.dismiss()

        val dialogView = layoutInflater.inflate(R.layout.dialog_urgent_support, null)
        val dialog = AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Crisis Helpline Buttons
        dialogView.findViewById<Button>(R.id.btn988Call).setOnClickListener {
            startDialIntent("988")
        }
        dialogView.findViewById<Button>(R.id.btn988Text).setOnClickListener {
            startSmsIntent("988")
        }
        dialogView.findViewById<Button>(R.id.btnIcall).setOnClickListener {
            startDialIntent("9152987821") // iCall number
        }
        dialogView.findViewById<Button>(R.id.btnAasra).setOnClickListener {
            startDialIntent("9820466726") // AASRA number
        }

        // Setup Trusted Circle RecyclerView
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerTrustedContacts)
        setupTrustedContactsRecyclerView(recyclerView)

        // Continue button
        dialogView.findViewById<Button>(R.id.btnContinue).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        urgentSupportDialog = dialog
    }

    private fun setupTrustedContactsRecyclerView(recyclerView: RecyclerView) {
        val adapter = CrisisTrustedContactAdapter(
            onCallClick = { person ->
                person.phoneNumber?.let { number ->
                    startDialIntent(number)
                }
            },
            onTextClick = { person ->
                person.phoneNumber?.let { number ->
                    startSmsIntent(number)
                }
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // TODO: Load trusted contacts from storage
        // For now, just show empty state
        adapter.updateContacts(emptyList())
    }

    private fun startDialIntent(number: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
        }
        startActivity(intent)
    }

    private fun startSmsIntent(number: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("sms:$number")
        }
        startActivity(intent)
    }

    private fun showEmergencyCallDialog() {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle("Call Crisis Support")
            .setMessage("Would you like to call the 988 Suicide & Crisis Lifeline? You'll be connected with a trained counselor who can help.")
            .setPositiveButton("Call Now") { _, _ ->
                startDialIntent("988")
            }
            .setNegativeButton("Not Now", null)
            .show()
    }

    override fun validateSection(): Boolean {
        val radioGroups = listOf(radioGroup1, radioGroup2, radioGroup3, radioGroup4)
        
        // Check if all questions are answered
        val unansweredGroups = radioGroups.filter { group -> 
            group.checkedRadioButtonId == -1 
        }

        if (unansweredGroups.isNotEmpty()) {
            Toast.makeText(context, 
                "Please answer all questions to continue", 
                Toast.LENGTH_SHORT).show()
            return false
        }

        // Check for high-risk responses and ensure crisis resources are shown
        val highRiskSelected = isHighRiskResponseSelected()
        if (highRiskSelected) {
            showUrgentSupportDialog()
        }

        return true
    }

    private fun isHighRiskResponseSelected(): Boolean {
        return radioGroup1.checkedRadioButtonId == R.id.q1_always ||
               radioGroup2.checkedRadioButtonId == R.id.q2_always ||
               radioGroup3.checkedRadioButtonId == R.id.q3_yes_thinking ||
               radioGroup3.checkedRadioButtonId == R.id.q3_yes_unsure ||
               radioGroup4.checkedRadioButtonId == R.id.q4_steps ||
               radioGroup4.checkedRadioButtonId == R.id.q4_attempted
    }

    override fun saveData() {
        // Create risk assessment data
        val assessmentData = mapOf(
            "feeling_heavy" to getRiskLevel(radioGroup1),
            "better_off_gone" to getRiskLevel(radioGroup2),
            "self_harm_thoughts" to getSelfHarmLevel(radioGroup3),
            "self_harm_steps" to getSelfHarmSteps(radioGroup4)
        )
        
        // TODO: Store data securely with appropriate encryption
        // For now, just log the data
        println("Safety Assessment: $assessmentData")
    }

    private fun getRiskLevel(group: RadioGroup): String {
        return when (group.checkedRadioButtonId) {
            R.id.q1_never, R.id.q2_never -> "never"
            R.id.q1_sometimes, R.id.q2_sometimes -> "sometimes"
            R.id.q1_often, R.id.q2_often -> "often"
            R.id.q1_always, R.id.q2_always -> "almost_always"
            else -> "unknown"
        }
    }

    private fun getSelfHarmLevel(group: RadioGroup): String {
        return when (group.checkedRadioButtonId) {
            R.id.q3_no -> "no"
            R.id.q3_yes_wouldnt -> "yes_no_action"
            R.id.q3_yes_unsure -> "yes_unsure"
            R.id.q3_yes_thinking -> "yes_planning"
            else -> "unknown"
        }
    }

    private fun getSelfHarmSteps(group: RadioGroup): String {
        return when (group.checkedRadioButtonId) {
            R.id.q4_no -> "no"
            R.id.q4_thought -> "thought_only"
            R.id.q4_steps -> "taken_steps"
            R.id.q4_attempted -> "attempted"
            else -> "unknown"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        urgentSupportDialog?.dismiss()
        urgentSupportDialog = null
    }
} 


