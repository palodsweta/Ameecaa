package com.ameekaa.app.persona

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ameekaa.app.R

class CrisisTrustedContactAdapter(
    private var trustedPeople: List<TrustedPerson> = listOf(),
    private val onCallClick: (TrustedPerson) -> Unit,
    private val onTextClick: (TrustedPerson) -> Unit
) : RecyclerView.Adapter<CrisisTrustedContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textName: TextView = view.findViewById(R.id.textName)
        val textRelationship: TextView = view.findViewById(R.id.textRelationship)
        val btnCall: Button = view.findViewById(R.id.btnCall)
        val btnText: Button = view.findViewById(R.id.btnText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_crisis_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val person = trustedPeople[position]
        
        holder.textName.text = person.name
        holder.textRelationship.text = person.relationship

        // Only enable contact buttons if phone number is available
        val hasPhone = !person.phoneNumber.isNullOrEmpty()
        holder.btnCall.isEnabled = hasPhone
        holder.btnText.isEnabled = hasPhone

        holder.btnCall.setOnClickListener { onCallClick(person) }
        holder.btnText.setOnClickListener { onTextClick(person) }
    }

    override fun getItemCount() = trustedPeople.size

    fun updateContacts(contacts: List<TrustedPerson>) {
        trustedPeople = contacts
        notifyDataSetChanged()
    }
} 


