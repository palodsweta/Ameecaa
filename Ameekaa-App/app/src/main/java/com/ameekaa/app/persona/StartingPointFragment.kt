package com.ameekaa.app.persona

import android.view.View
import android.widget.RadioGroup
import android.widget.Toast
import com.ameekaa.app.R

class StartingPointFragment : BasePersonaFragment() {
    
    private lateinit var radioGroups: List<RadioGroup>

    override fun getLayoutResourceId(): Int = R.layout.fragment_starting_point

    override fun setupViews(view: View) {
        radioGroups = listOf(
            view.findViewById(R.id.radioGroup1),
            view.findViewById(R.id.radioGroup2),
            view.findViewById(R.id.radioGroup3),
            view.findViewById(R.id.radioGroup4),
            view.findViewById(R.id.radioGroup5),
            view.findViewById(R.id.radioGroup6),
            view.findViewById(R.id.radioGroup7)
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
        // Save answers (0 = Never, 1 = Sometimes, 2 = Often, 3 = Almost Always)
        val answers = radioGroups.map { group ->
            when (group.checkedRadioButtonId) {
                group.getChildAt(0).id -> 0 // Never
                group.getChildAt(1).id -> 1 // Sometimes
                group.getChildAt(2).id -> 2 // Often
                group.getChildAt(3).id -> 3 // Almost Always
                else -> -1
            }
        }
        
        // TODO: Store answers in shared preferences or database
        // For now, just log them
        answers.forEachIndexed { index, answer ->
            println("Question ${index + 1}: $answer")
        }
    }
} 


