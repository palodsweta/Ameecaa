package com.ameekaa.app.persona

import android.app.AlertDialog
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ameekaa.app.R

class TrustedCircleFragment : BasePersonaFragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddPerson: Button
    private lateinit var adapter: TrustedPersonAdapter

    private val relationships = arrayOf(
        "Family Member",
        "Friend",
        "Partner / Spouse",
        "Therapist / Counselor",
        "Mentor",
        "Colleague",
        "Other"
    )

    override fun getLayoutResourceId(): Int = R.layout.fragment_trusted_circle

    override fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerTrustedPeople)
        btnAddPerson = view.findViewById(R.id.btnAddTrustedPerson)

        setupRecyclerView()
        setupAddButton()
    }

    private fun setupRecyclerView() {
        adapter = TrustedPersonAdapter(
            onEditClick = { person -> showAddEditDialog(person) },
            onDeleteClick = { person -> showDeleteConfirmation(person) }
        )
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@TrustedCircleFragment.adapter
        }
    }

    private fun setupAddButton() {
        btnAddPerson.setOnClickListener {
            showAddEditDialog()
        }
    }

    private fun showAddEditDialog(existingPerson: TrustedPerson? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_trusted_person, null)
        val dialog = AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setView(dialogView)
            .create()

        val editName = dialogView.findViewById<EditText>(R.id.editName)
        val spinnerRelationship = dialogView.findViewById<Spinner>(R.id.spinnerRelationship)
        val editSupport = dialogView.findViewById<EditText>(R.id.editSupport)
        val editPhone = dialogView.findViewById<EditText>(R.id.editPhone)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        // Setup spinner
        ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            relationships
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerRelationship.adapter = adapter
        }

        // Pre-fill data if editing
        existingPerson?.let { person ->
            editName.setText(person.name)
            spinnerRelationship.setSelection(relationships.indexOf(person.relationship))
            editSupport.setText(person.supportDescription)
            editPhone.setText(person.phoneNumber)
        }

        btnSave.setOnClickListener {
            val name = editName.text.toString().trim()
            val relationship = relationships[spinnerRelationship.selectedItemPosition]
            val support = editSupport.text.toString().trim()
            val phone = editPhone.text.toString().trim()

            if (name.isEmpty() || support.isEmpty()) {
                Toast.makeText(context, 
                    "Please fill in all required fields", 
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val person = TrustedPerson(
                id = existingPerson?.id ?: "",
                name = name,
                relationship = relationship,
                supportDescription = support,
                phoneNumber = phone.ifEmpty { null }
            )

            if (existingPerson == null) {
                adapter.addPerson(person)
            } else {
                adapter.removePerson(existingPerson)
                adapter.addPerson(person)
            }

            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteConfirmation(person: TrustedPerson) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle("Remove Contact")
            .setMessage("Are you sure you want to remove ${person.name} from your trusted circle?")
            .setPositiveButton("Remove") { _, _ ->
                adapter.removePerson(person)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun validateSection(): Boolean {
        // At least one trusted person should be added
        if (adapter.itemCount == 0) {
            Toast.makeText(context, 
                "Please add at least one person to your trusted circle", 
                Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    override fun saveData() {
        // TODO: Store trusted circle data in encrypted storage
        // For now, just log the data
        println("Trusted circle size: ${adapter.itemCount}")
    }
} 


