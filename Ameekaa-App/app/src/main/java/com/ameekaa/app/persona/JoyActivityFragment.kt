package com.ameekaa.app.persona

import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.ameekaa.app.R

class JoyActivityFragment : BasePersonaFragment() {
    
    private lateinit var editHobbies: EditText
    private lateinit var editMedia: EditText
    private lateinit var editMovement: EditText

    override fun getLayoutResourceId(): Int = R.layout.fragment_joy_activity

    override fun setupViews(view: View) {
        editHobbies = view.findViewById(R.id.editHobbies)
        editMedia = view.findViewById(R.id.editMedia)
        editMovement = view.findViewById(R.id.editMovement)
    }

    override fun validateSection(): Boolean {
        val fields = listOf(
            editHobbies to "hobbies and passions",
            editMedia to "comfort media",
            editMovement to "movement activities"
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
        val activityData = mapOf(
            "hobbies" to editHobbies.text.toString().trim(),
            "media" to editMedia.text.toString().trim(),
            "movement" to editMovement.text.toString().trim()
        )
        
        // TODO: Store data in shared preferences or database
        // For now, just log the data
        activityData.forEach { (category, preferences) ->
            println("$category: $preferences")
        }
    }
} 


