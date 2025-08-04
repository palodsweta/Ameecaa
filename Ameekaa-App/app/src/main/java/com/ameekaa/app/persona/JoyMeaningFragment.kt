package com.ameekaa.app.persona

import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.ameekaa.app.R

class JoyMeaningFragment : BasePersonaFragment() {
    
    private lateinit var editValues: EditText
    private lateinit var editLegacy: EditText
    private lateinit var editSpiritual: EditText
    private lateinit var editHelping: EditText

    override fun getLayoutResourceId(): Int = R.layout.fragment_joy_meaning

    override fun setupViews(view: View) {
        editValues = view.findViewById(R.id.editValues)
        editLegacy = view.findViewById(R.id.editLegacy)
        editSpiritual = view.findViewById(R.id.editSpiritual)
        editHelping = view.findViewById(R.id.editHelping)
    }

    override fun validateSection(): Boolean {
        val fields = listOf(
            editValues to "core values",
            editLegacy to "proud moments",
            editSpiritual to "sources of inner strength",
            editHelping to "experiences of helping others"
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
        val meaningData = mapOf(
            "values" to editValues.text.toString().trim(),
            "legacy" to editLegacy.text.toString().trim(),
            "spiritual" to editSpiritual.text.toString().trim(),
            "helping" to editHelping.text.toString().trim()
        )
        
        // TODO: Store data in shared preferences or database
        // For now, just log the data
        meaningData.forEach { (category, content) ->
            println("$category: $content")
        }
    }
} 


