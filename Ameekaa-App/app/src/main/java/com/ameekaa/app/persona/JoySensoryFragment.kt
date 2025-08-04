package com.ameekaa.app.persona

import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.ameekaa.app.R

class JoySensoryFragment : BasePersonaFragment() {
    
    private lateinit var editSight: EditText
    private lateinit var editSound: EditText
    private lateinit var editSmell: EditText
    private lateinit var editTaste: EditText
    private lateinit var editTouch: EditText

    override fun getLayoutResourceId(): Int = R.layout.fragment_joy_sensory

    override fun setupViews(view: View) {
        editSight = view.findViewById(R.id.editSight)
        editSound = view.findViewById(R.id.editSound)
        editSmell = view.findViewById(R.id.editSmell)
        editTaste = view.findViewById(R.id.editTaste)
        editTouch = view.findViewById(R.id.editTouch)
    }

    override fun validateSection(): Boolean {
        val fields = listOf(
            editSight to "visual comforts",
            editSound to "sound preferences",
            editSmell to "comforting scents",
            editTaste to "comfort foods/drinks",
            editTouch to "comforting textures"
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
        val sensoryData = mapOf(
            "sight" to editSight.text.toString().trim(),
            "sound" to editSound.text.toString().trim(),
            "smell" to editSmell.text.toString().trim(),
            "taste" to editTaste.text.toString().trim(),
            "touch" to editTouch.text.toString().trim()
        )
        
        // TODO: Store data in shared preferences or database
        // For now, just log the data
        sensoryData.forEach { (sense, preferences) ->
            println("$sense: $preferences")
        }
    }
} 


