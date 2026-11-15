package com.example.ui.memory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MemoryEntry
import com.example.ui.theme.LocalJarvisColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    memories: List<MemoryEntry>,
    memoryPermissionGranted: Boolean,
    onToggleMemoryPermission: (Boolean) -> Unit,
    onAddMemory: (category: String, content: String) -> Unit,
    onUpdateMemory: (memoryId: String, category: String, content: String) -> Unit,
    onDeleteMemory: (memoryId: String) -> Unit,
    onClearAllMemories: () -> Unit,
    onBack: () -> Unit
) {
    val jarvisColors = LocalJarvisColors.current
    var selectedCategory by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var showAddOrEditDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntry?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    val filteredMemories = remember(memories, selectedCategory, searchQuery) {
        memories.filter { mem ->
            val matchesCat = selectedCategory == "all" || mem.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                mem.content.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Memory Vault", style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("memory_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (memories.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearAllConfirm = true },
                            modifier = Modifier.testTag("clear_all_memories_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = "Clear All Memories",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingMemory = null
                    showAddOrEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_memory_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Memory")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Permission Card
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, jarvisColors.subtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Psychology,
                                        contentDescription = null,
                                        tint = jarvisColors.accentCyan
                                    )
                                    Column {
                                        Text(
                                            text = "Persistent Memory",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Let Jarvis remember preferences & ongoing projects",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = memoryPermissionGranted,
                                    onCheckedChange = onToggleMemoryPermission,
                                    modifier = Modifier.testTag("memory_permission_switch")
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Passwords, keys, and sensitive credentials are never stored.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Search & Category Filters
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search stored memories...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("memory_search_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "all" to "All (${memories.size})",
                            "preference" to "Preferences",
                            "project" to "Projects",
                            "fact" to "Facts",
                            "context" to "Context"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = selectedCategory == key,
                                onClick = { selectedCategory = key },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                if (filteredMemories.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Psychology,
                                    contentDescription = null,
                                    tint = jarvisColors.accentCyan,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = if (memories.isEmpty()) "No memories stored yet" else "No matching memories",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "As you chat with Jarvis, useful preferences and project context appear here — or tap + to add one manually.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredMemories, key = { it.id }) { memory ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, jarvisColors.subtleBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("memory_card_${memory.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when (memory.category) {
                                            "preference" -> jarvisColors.accentCyan.copy(alpha = 0.16f)
                                            "project" -> jarvisColors.accentAmber.copy(alpha = 0.18f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            text = memory.category.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when (memory.category) {
                                                "preference" -> jarvisColors.accentCyan
                                                "project" -> jarvisColors.accentAmber
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = memory.content,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            editingMemory = memory
                                            showAddOrEditDialog = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = "Edit memory",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteMemory(memory.id) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Delete memory",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddOrEditDialog) {
        MemoryEditorDialog(
            initialCategory = editingMemory?.category ?: "preference",
            initialContent = editingMemory?.content ?: "",
            isEditing = editingMemory != null,
            onDismiss = { showAddOrEditDialog = false },
            onSave = { category, content ->
                val target = editingMemory
                if (target != null) {
                    onUpdateMemory(target.id, category, content)
                } else {
                    onAddMemory(category, content)
                }
                showAddOrEditDialog = false
            }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear all memories?") },
            text = { Text("This permanently deletes all ${memories.size} saved memories from your cloud vault.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllMemories()
                        showClearAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MemoryEditorDialog(
    initialCategory: String,
    initialContent: String,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var category by remember { mutableStateOf(initialCategory) }
    var content by remember { mutableStateOf(initialContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Memory" else "Add Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("preference", "project", "fact", "context").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("What should Jarvis remember?") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_content_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (content.isNotBlank()) onSave(category, content.trim()) },
                enabled = content.isNotBlank(),
                modifier = Modifier.testTag("save_memory_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
