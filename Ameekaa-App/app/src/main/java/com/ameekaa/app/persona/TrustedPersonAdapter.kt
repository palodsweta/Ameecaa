package com.ameekaa.app.persona

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ameekaa.app.R

class TrustedPersonAdapter(
    private var trustedPeople: MutableList<TrustedPerson> = mutableListOf(),
    private val onEditClick: (TrustedPerson) -> Unit,
    private val onDeleteClick: (TrustedPerson) -> Unit
) : RecyclerView.Adapter<TrustedPersonAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textName: TextView = view.findViewById(R.id.textName)
        val textRelationship: TextView = view.findViewById(R.id.textRelationship)
        val textSupport: TextView = view.findViewById(R.id.textSupport)
        val btnEdit: View = view.findViewById(R.id.btnEdit)
        val btnDelete: View = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trusted_person, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val person = trustedPeople[position]
        holder.textName.text = person.name
        holder.textRelationship.text = person.relationship
        holder.textSupport.text = person.supportDescription

        holder.btnEdit.setOnClickListener { onEditClick(person) }
        holder.btnDelete.setOnClickListener { onDeleteClick(person) }
    }

    override fun getItemCount() = trustedPeople.size

    fun updateData(newPeople: List<TrustedPerson>) {
        trustedPeople.clear()
        trustedPeople.addAll(newPeople)
        notifyDataSetChanged()
    }

    fun addPerson(person: TrustedPerson) {
        trustedPeople.add(person)
        notifyItemInserted(trustedPeople.size - 1)
    }

    fun removePerson(person: TrustedPerson) {
        val position = trustedPeople.indexOfFirst { it.id == person.id }
        if (position != -1) {
            trustedPeople.removeAt(position)
            notifyItemRemoved(position)
        }
    }
} 


