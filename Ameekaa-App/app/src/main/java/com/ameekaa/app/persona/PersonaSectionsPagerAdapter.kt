package com.ameekaa.app.persona

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class PersonaSectionsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 10 // Total number of sections

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> StartingPointFragment()
            1 -> PersonalityPreferencesFragment()
            2 -> JoySensoryFragment()
            3 -> JoyActivityFragment()
            4 -> JoyMindFragment()
            5 -> JoyMeaningFragment()
            6 -> ConversationsFragment()
            7 -> TrustedCircleFragment()
            8 -> SafetyFragment()
            9 -> FinalPageFragment()
            else -> StartingPointFragment() // Should never happen
        }
    }
} 


