package com.example.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
import com.example.ui.theme.LocalJarvisColors

@Composable
fun ChatDrawerContent(
    conversations: List<Conversation>,
    activeConversationId: String?,
    memoryCount: Int,
    userProfile: UserProfile?,
    onNewChat: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onTogglePinConversation: (String, Boolean) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenMemoryVault: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val jarvisColors = LocalJarvisColors.current
    var searchQuery by remember { mutableStateOf("") }
    var renamingConv by remember { mutableStateOf<Conversation?>(null) }
    var deletingConv by remember { mutableStateOf<Conversation?>(null) }

    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.lastMessagePreview.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .width(304.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        // Brand Header + New Chat Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_jarvis_core_avatar),
                    contentDescription = "Jarvis AI",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = "JARVIS AI",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onNewChat,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("new_conversation_button")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Conversation", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search chats...", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("drawer_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Conversation List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (filteredConversations.isEmpty()) {
                item {
                    Text(
                        text = if (conversations.isEmpty()) "No saved chats yet" else "No matching chats",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                items(filteredConversations, key = { it.id }) { conv ->
                    ConversationListItem(
                        conversation = conv,
                        isActive = conv.id == activeConversationId,
                        onSelect = { onSelectConversation(conv.id) },
                        onRename = { renamingConv = conv },
                        onTogglePin = { onTogglePinConversation(conv.id, !conv.pinned) },
                        onDelete = { deletingConv = conv }
                    )
                }
            }
        }

        HorizontalDivider(color = jarvisColors.subtleBorder, modifier = Modifier.padding(vertical = 8.dp))

        // Memory Vault Tile
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenMemoryVault)
                .testTag("drawer_memory_vault_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                        tint = jarvisColors.accentCyan
                    )
                    Text("Memory Vault", style = MaterialTheme.typography.titleSmall)
                }
                if (memoryCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text(memoryCount.toString())
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // User Profile & Settings Tile
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, jarvisColors.subtleBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSettings)
                .testTag("drawer_settings_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (!userProfile?.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = userProfile?.photoUrl,
                                contentDescription = "Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (userProfile?.displayName?.firstOrNull() ?: 'U').uppercase(),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile?.displayName ?: "User",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Settings & Profile",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Open Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (renamingConv != null) {
        val target = renamingConv!!
        var newTitle by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renamingConv = null },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onRenameConversation(target.id, newTitle.trim())
                            renamingConv = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingConv = null }) { Text("Cancel") }
            }
        )
    }

    if (deletingConv != null) {
        val target = deletingConv!!
        AlertDialog(
            onDismissRequest = { deletingConv = null },
            title = { Text("Delete Conversation?") },
            text = { Text("\"${target.title}\" and all its messages will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteConversation(target.id)
                        deletingConv = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingConv = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ConversationListItem(
    conversation: Conversation,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val jarvisColors = LocalJarvisColors.current
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (isActive) BorderStroke(1.dp, jarvisColors.accentCyan.copy(alpha = 0.45f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("conversation_item_${conversation.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (conversation.pinned) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    tint = jarvisColors.accentAmber,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 2.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (conversation.lastMessagePreview.isNotBlank()) {
                    Text(
                        text = conversation.lastMessagePreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Conversation actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (conversation.pinned) "Unpin" else "Pin") },
                        leadingIcon = {
                            Icon(Icons.Outlined.PushPin, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Edit, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
