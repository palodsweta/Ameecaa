package com.ameekaa.app.persona

import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.ameekaa.app.R

class JoyMindFragment : BasePersonaFragment() {
    
    private lateinit var editMentalReset: EditText
    private lateinit var editSelfTalk: EditText
    private lateinit var editCuriosity: EditText
    private lateinit var editFocusTools: EditText

    override fun getLayoutResourceId(): Int = R.layout.fragment_joy_mind

    override fun setupViews(view: View) {
        editMentalReset = view.findViewById(R.id.editMentalReset)
        editSelfTalk = view.findViewById(R.id.editSelfTalk)
        editCuriosity = view.findViewById(R.id.editCuriosity)
        editFocusTools = view.findViewById(R.id.editFocusTools)
    }

    override fun validateSection(): Boolean {
        val fields = listOf(
            editMentalReset to "mental reset techniques",
            editSelfTalk to "self-talk phrases",
            editCuriosity to "curiosity topics",
            editFocusTools to "focus tools"
        )

        val emptyFields = fields.filter { (field, _) -> 
            field.text.toString().trim().isEmpty() 
        }

        if (emptyFields.isNotEmpty()) {
            val missingField = emptyFields.first().second
            Toast.makeText(context, 
                "Please share your $missingField to continue", 
                Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    override fun saveData() {
        val mindData = mapOf(
            "mental_reset" to editMentalReset.text.toString().trim(),
            "self_talk" to editSelfTalk.text.toString().trim(),
            "curiosity" to editCuriosity.text.toString().trim(),
            "focus_tools" to editFocusTools.text.toString().trim()
        )
        
        // TODO: Store data in shared preferences or database
        // For now, just log the data
        mindData.forEach { (category, content) ->
            println("$category: $content")
        }
    }
} 


