package com.ameekaa.app.persona

import android.view.View
import android.widget.RadioGroup
import android.widget.Toast
import com.ameekaa.app.R

class PersonalityPreferencesFragment : BasePersonaFragment() {
    
    private lateinit var radioGroups: List<RadioGroup>

    override fun getLayoutResourceId(): Int = R.layout.fragment_personality_preferences

    override fun setupViews(view: View) {
        radioGroups = listOf(
            view.findViewById(R.id.radioGroup1),
            view.findViewById(R.id.radioGroup2),
            view.findViewById(R.id.radioGroup3),
            view.findViewById(R.id.radioGroup4),
            view.findViewById(R.id.radioGroup5)
        )
    }

    override fun validateSection(): Boolean {
        // Check if all questions are answered
        val unansweredQuestions = radioGroups.filterIndexed { index, group -> 
            group.checkedRadioButtonId == -1 
        }

        if (unansweredQuestions.isNotEmpty()) {
            Toast.makeText(context, 
                "Please answer all questions to continue", 
                Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    override fun saveData() {
        // Save answers (0 = Choice A, 1 = Choice B)
        val answers = radioGroups.map { group ->
            when (group.checkedRadioButtonId) {
                group.getChildAt(0).id -> 0 // Choice A
                group.getChildAt(1).id -> 1 // Choice B
                else -> -1
            }
        }
        
        // TODO: Store answers in shared preferences or database
        // For now, just log them with their meanings
        val questionTitles = listOf(
            "Weekend Preference",
            "Planning Style",
            "Me Time Activity",
            "Problem-Solving Approach",
            "Change Response"
        )
        
        answers.forEachIndexed { index, answer ->
            val preference = when(index) {
                0 -> if (answer == 0) "Introspective" else "Social"
                1 -> if (answer == 0) "Structured" else "Spontaneous"
                2 -> if (answer == 0) "Creative" else "Organized"
                3 -> if (answer == 0) "Emotional" else "Logical"
                4 -> if (answer == 0) "Adaptable" else "Stability-seeking"
                else -> "Unknown"
            }
            println("${questionTitles[index]}: $preference")
        }
    }
} 


