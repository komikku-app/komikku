package eu.kanade.presentation.category.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import dev.icerock.moko.resources.StringResource
import eu.kanade.core.preference.asToggleableState
import eu.kanade.presentation.category.buildCategoryHierarchy
import eu.kanade.presentation.category.visualName
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
fun CategoryCreateDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String, Long?) -> Unit,
    categories: ImmutableList<String>,
    parentOptions: ImmutableList<Category> = persistentListOf(),
    initialParentId: Long? = null,
    // SY -->
    title: String = stringResource(MR.strings.action_add_category),
    extraMessage: String? = null,
    alreadyExistsError: StringResource = MR.strings.error_category_exists,
    // SY <--
) {
    var name by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf(initialParentId) }

    val focusRequester = remember { FocusRequester() }
    val nameAlreadyExists = remember(name) { categories.contains(name) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = name.isNotEmpty() && !nameAlreadyExists,
                onClick = {
                    onCreate(name, parentId)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            // SY -->
            Text(text = title)
            // SY <--
        },
        text = {
            // SY -->
            Column {
                extraMessage?.let { Text(it) }
                // SY <--

                OutlinedTextField(
                    modifier = Modifier
                        .focusRequester(focusRequester),
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(text = stringResource(MR.strings.name))
                    },
                    supportingText = {
                        val msgRes = if (name.isNotEmpty() && nameAlreadyExists) {
                            // SY -->
                            alreadyExistsError
                            // SY <--
                        } else {
                            MR.strings.information_required_plain
                        }
                        Text(text = stringResource(msgRes))
                    },
                    isError = name.isNotEmpty() && nameAlreadyExists,
                    singleLine = true,
                )
                ParentCategorySelector(
                    parentOptions = parentOptions,
                    selectedParentId = parentId,
                    onSelectParent = { parentId = it },
                )
                // SY -->
            }
            // SY <--
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun CategoryRenameDialog(
    onDismissRequest: () -> Unit,
    onRename: (String, Long?) -> Unit,
    categories: ImmutableList<String>,
    category: String,
    parentOptions: ImmutableList<Category> = persistentListOf(),
    initialParentId: Long? = null,
    categoryHasChildren: Boolean = false,
) {
    var name by remember { mutableStateOf(category) }
    var valueHasChanged by remember { mutableStateOf(false) }
    var parentId by remember { mutableStateOf(initialParentId) }

    val focusRequester = remember { FocusRequester() }
    val nameAlreadyExists = remember(name) { categories.contains(name) && name != category }
    val parentHasChanged = parentId != initialParentId
    val canChangeName = valueHasChanged && !nameAlreadyExists
    val canChangeParent = parentHasChanged && !(categoryHasChildren && parentId != initialParentId)
    val hasChanges = canChangeName || canChangeParent

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = hasChanges,
                onClick = {
                    onRename(name, parentId)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_rename_category))
        },
        text = {
            OutlinedTextField(
                modifier = Modifier.focusRequester(focusRequester),
                value = name,
                onValueChange = {
                    valueHasChanged = name != it
                    name = it
                },
                label = { Text(text = stringResource(MR.strings.name)) },
                supportingText = {
                    val msgRes = if (valueHasChanged && nameAlreadyExists) {
                        MR.strings.error_category_exists
                    } else {
                        MR.strings.information_required_plain
                    }
                    Text(text = stringResource(msgRes))
                },
                isError = valueHasChanged && nameAlreadyExists,
                singleLine = true,
            )
            ParentCategorySelector(
                parentOptions = parentOptions,
                selectedParentId = parentId,
                onSelectParent = { parentId = it },
                categoryHasChildren = categoryHasChildren,
            )
        },
    )

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun CategoryDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    // SY -->
    category: String = "",
    title: String = stringResource(MR.strings.delete_category),
    text: String = stringResource(MR.strings.delete_category_confirmation, category),
    // SY <--
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = {
                onDelete()
                onDismissRequest()
            }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            // SY -->
            Text(text = title)
            // SY <--
        },
        text = {
            // SY -->
            Text(text = text)
            // SY <--
        },
    )
}

@Composable
private fun ParentCategorySelector(
    parentOptions: ImmutableList<Category>,
    selectedParentId: Long?,
    onSelectParent: (Long?) -> Unit,
    categoryHasChildren: Boolean = false,
) {
    if (parentOptions.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val noneLabel = stringResource(MR.strings.none)
    val selectedCategory = remember(selectedParentId, parentOptions) {
        parentOptions.firstOrNull { it.id == selectedParentId }
    }
    val selectedLabel = if (selectedCategory != null) selectedCategory.visualName else noneLabel

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.padding.small),
    ) {
        TextButton(onClick = { if (!categoryHasChildren) expanded = true }, enabled = !categoryHasChildren) {
            Text(selectedLabel)
        }
        if (!categoryHasChildren) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.none)) },
                    onClick = {
                        onSelectParent(null)
                        expanded = false
                    },
                )
                parentOptions.forEach { parent ->
                    DropdownMenuItem(
                        text = { Text(parent.visualName) },
                        onClick = {
                            onSelectParent(parent.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ChangeCategoryDialog(
    initialSelection: ImmutableList<CheckboxState<Category>>,
    onDismissRequest: () -> Unit,
    onEditCategories: () -> Unit,
    onConfirm: (List<Long>, List<Long>) -> Unit,
) {
    if (initialSelection.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                tachiyomi.presentation.core.components.material.TextButton(
                    onClick = {
                        onDismissRequest()
                        onEditCategories()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_edit_categories))
                }
            },
            title = {
                Text(text = stringResource(MR.strings.action_move_category))
            },
            text = {
                Text(text = stringResource(MR.strings.information_empty_category_dialog))
            },
        )
        return
    }
    var selection by remember { mutableStateOf(initialSelection) }
    var expandedParents by remember { mutableStateOf(setOf<Long>()) }

    // Check which parents have children
    val parentChildMap by remember(selection) {
        mutableStateOf(
            selection.groupBy { it.value.parentId }
                .filterKeys { it != null }
                .mapKeys { it.key!! },
        )
    }

    val orderedSelection by remember(selection, expandedParents) {
        val selectionMap = selection.associateBy { it.value.id }
        mutableStateOf(
            buildCategoryHierarchy(selection.map { it.value })
                .mapNotNull { entry ->
                    selectionMap[entry.category.id]?.let { CheckboxEntry(it, entry.depth) }
                }
                .filter { entry ->
                    // Show all parents
                    if (entry.checkbox.value.parentId == null) {
                        true
                    } // Show children only if their parent is expanded
                    else {
                        expandedParents.contains(entry.checkbox.value.parentId)
                    }
                }
                .toImmutableList(),
        )
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Row {
                tachiyomi.presentation.core.components.material.TextButton(onClick = {
                    onDismissRequest()
                    onEditCategories()
                }) {
                    Text(text = stringResource(MR.strings.action_edit))
                }
                Spacer(modifier = Modifier.weight(1f))
                tachiyomi.presentation.core.components.material.TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                tachiyomi.presentation.core.components.material.TextButton(
                    onClick = {
                        onDismissRequest()
                        onConfirm(
                            selection
                                .filter { it is CheckboxState.State.Checked || it is CheckboxState.TriState.Include }
                                .map { it.value.id },
                            selection
                                .filter { it is CheckboxState.State.None || it is CheckboxState.TriState.None }
                                .map { it.value.id },
                        )
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_move_category))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                orderedSelection.forEach { entry ->
                    val checkbox = entry.checkbox
                    val isParent = checkbox.value.parentId == null
                    val isExpanded = expandedParents.contains(checkbox.value.id)
                    val hasChildren = parentChildMap.containsKey(checkbox.value.id)
                    val onChange: (CheckboxState<Category>) -> Unit = {
                        val index = selection.indexOf(it)
                        if (index != -1) {
                            val mutableList = selection.toMutableList()
                            mutableList[index] = it.next()
                            selection = mutableList.toList().toImmutableList()
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isParent && hasChildren) {
                                    // Toggle expand/collapse only for parents with children
                                    expandedParents = if (isExpanded) {
                                        expandedParents - checkbox.value.id
                                    } else {
                                        expandedParents + checkbox.value.id
                                    }
                                } else {
                                    // Toggle checkbox for all items (parents without children and all children)
                                    onChange(checkbox)
                                }
                            }
                            .padding(start = MaterialTheme.padding.medium * entry.depth.coerceAtLeast(0).toFloat()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Show checkbox on the left for all items
                        when (checkbox) {
                            is CheckboxState.TriState -> {
                                TriStateCheckbox(
                                    state = checkbox.asToggleableState(),
                                    onClick = { onChange(checkbox) },
                                )
                            }
                            is CheckboxState.State -> {
                                Checkbox(
                                    checked = checkbox.isChecked,
                                    onCheckedChange = { onChange(checkbox) },
                                )
                            }
                        }

                        Text(
                            text = checkbox.value.visualName,
                            modifier = Modifier
                                .padding(horizontal = MaterialTheme.padding.medium)
                                .weight(1f),
                        )

                        // Show expand/collapse indicator on the right for parents with children
                        if (isParent && hasChildren) {
                            Text(
                                text = if (isExpanded) "▼" else "▶",
                                modifier = Modifier
                                    .padding(end = MaterialTheme.padding.medium)
                                    .clickable(
                                        enabled = true,
                                        onClick = {
                                            expandedParents = if (isExpanded) {
                                                expandedParents - checkbox.value.id
                                            } else {
                                                expandedParents + checkbox.value.id
                                            }
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
        },
    )
}

private data class CheckboxEntry(
    val checkbox: CheckboxState<Category>,
    val depth: Int,
)
