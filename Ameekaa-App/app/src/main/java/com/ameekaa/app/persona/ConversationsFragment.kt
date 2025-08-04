package com.ameekaa.app.persona

import android.view.View
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.Toast
import com.ameekaa.app.R

class ConversationsFragment : BasePersonaFragment() {
    
    private lateinit var checkSelfTalk: CheckBox
    private lateinit var checkPartner: CheckBox
    private lateinit var checkFamily: CheckBox
    private lateinit var checkFriends: CheckBox
    private lateinit var checkColleagues: CheckBox
    private lateinit var radioGroupChallenges: RadioGroup

    override fun getLayoutResourceId(): Int = R.layout.fragment_conversations

    override fun setupViews(view: View) {
        checkSelfTalk = view.findViewById(R.id.checkSelfTalk)
        checkPartner = view.findViewById(R.id.checkPartner)
        checkFamily = view.findViewById(R.id.checkFamily)
        checkFriends = view.findViewById(R.id.checkFriends)
        checkColleagues = view.findViewById(R.id.checkColleagues)
        radioGroupChallenges = view.findViewById(R.id.radioGroupChallenges)
    }

    override fun validateSection(): Boolean {
        val checkboxes = listOf(
            checkSelfTalk,
            checkPartner,
            checkFamily,
            checkFriends,
            checkColleagues
        )

        // Check if at least one conversation type is selected
        if (checkboxes.none { it.isChecked }) {
            Toast.makeText(context, 
                "Please select at least one type of frequent conversation", 
                Toast.LENGTH_SHORT).show()
            return false
        }

        // No validation for challenges as it's optional
        return true
    }

    override fun saveData() {
        // Save frequent conversations
        val frequentConversations = mutableListOf<String>()
        if (checkSelfTalk.isChecked) frequentConversations.add("self_talk")
        if (checkPartner.isChecked) frequentConversations.add("partner")
        if (checkFamily.isChecked) frequentConversations.add("family")
        if (checkFriends.isChecked) frequentConversations.add("friends")
        if (checkColleagues.isChecked) frequentConversations.add("colleagues")

        // Save challenging conversations
        val challengingConversation = when (radioGroupChallenges.checkedRadioButtonId) {
            R.id.radioPartnerFamily -> "partner_family"
            R.id.radioWorkSchool -> "work_school"
            R.id.radioSocial -> "social"
            R.id.radioOkay -> "none"
            R.id.radioPreferNotSay -> "prefer_not_say"
            else -> null
        }
        
        // TODO: Store data in shared preferences or database
        // For now, just log the data
        println("Frequent conversations: $frequentConversations")
        println("Challenging conversation: $challengingConversation")
    }
} 


