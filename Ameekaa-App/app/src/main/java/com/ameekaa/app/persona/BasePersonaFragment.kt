package com.ameekaa.app.persona

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

abstract class BasePersonaFragment : Fragment() {
    
    abstract fun getLayoutResourceId(): Int
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(getLayoutResourceId(), container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
    }

    abstract fun setupViews(view: View)

    // Method to validate the section's data before proceeding
    open fun validateSection(): Boolean {
        return true
    }

    // Method to save the section's data
    open fun saveData() {
        // Default implementation does nothing
    }
} 


