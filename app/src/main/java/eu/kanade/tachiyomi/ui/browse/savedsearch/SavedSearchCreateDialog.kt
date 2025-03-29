-okButton.setOnClickListener { createSavedSearch(nameInput.text.toString()) }
okButton.setOnClickListener {
    val name = nameInput.text.toString().trim()
    databaseHandler.getSavedSearchByName(name) { existing ->
        if (existing != null) {
            showConfirmationDialog(
                message = "A saved search with this name already exists. Overwrite?",
                onConfirm = { createSavedSearch(name, overwrite = true) },
                onCancel = { /* Allow user to modify the name */ }
            )
        } else {
            createSavedSearch(name, overwrite = false)
        }
    }
}
