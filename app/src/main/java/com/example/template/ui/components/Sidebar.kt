package com.example.template.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.template.DemoUiState
import com.example.template.data.DemoSection
import com.example.template.ui.theme.Palette

/**
 * The sidebar — the pattern this template exists to demo.
 *
 * Reading top to bottom it is: a brand header, small uppercase group labels, a pill-shaped
 * text field, a segmented selector, switch and plain rows, action rows, and an account-row
 * footer. That is the whole vocabulary; a real app styles the same pieces with its own copy.
 *
 * Two things here are load-bearing and easy to get wrong:
 *
 *  * **`onFieldFocus`** — the drawer's back handling needs to know that a text field is being
 *    edited, otherwise back closes the panel *and* dismisses the keyboard in one press (see
 *    `docs/PITFALLS.md`, "Back and the keyboard").
 *  * **`windowInsetsPadding(WindowInsets.statusBars)`** — the panel is drawn edge to edge, so
 *    without it the first row sits under the clock.
 */
@Composable
fun DemoSidebarContent(
    state: DemoUiState,
    onDisplayNameChange: (String) -> Unit,
    onSection: (DemoSection) -> Unit,
    onDenseRows: (Boolean) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onWork: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onFieldFocus: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var focusedField by remember { mutableStateOf<Int?>(null) }
    val reportFocus: (Int, Boolean) -> Unit = { id, focused ->
        focusedField = if (focused) id else focusedField?.takeIf { it != id }
        onFieldFocus(focusedField != null)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        SidebarHeader(state = state, onClose = onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            GroupLabel("Profile")
            PillField(
                value = state.displayName,
                onValueChange = onDisplayNameChange,
                placeholder = "Display name",
                onFocusChange = { reportFocus(0, it) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryButton(
                    label = "Save",
                    icon = Icons.Outlined.Save,
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlineButton(label = "Clear", onClick = onClear)
            }
            Text(
                text = "Saved to SharedPreferences: the value survives a restart.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )

            GroupLabel("Section")
            SegmentedSelector(
                options = DemoSection.entries.map { it.label },
                selectedIndex = DemoSection.entries.indexOf(state.section),
                onSelect = { index -> onSection(DemoSection.entries[index]) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            GroupLabel("Options")
            SwitchRow(
                label = "Dense rows",
                checked = state.denseRows,
                onCheckedChange = onDenseRows,
            )

            GroupLabel("Actions")
            ActionRow(
                label = if (state.busy) "Working…" else "Simulate work",
                icon = Icons.Outlined.PlayArrow,
                onClick = onWork,
            )
            ActionRow(
                label = "Reset everything",
                icon = Icons.Outlined.RestartAlt,
                onClick = onReset,
            )
            Spacer(Modifier.height(12.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AccountRow(state = state, onReset = onReset, onClose = onClose)
    }
}

@Composable
private fun SidebarHeader(state: DemoUiState, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Palette.BrandBlue.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (state.displayName.firstOrNull() ?: 'T').uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.LightSurface,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = state.displayName.ifBlank { "App Template" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.section.blurb,
                style = MaterialTheme.typography.labelSmall,
                color = if (state.busy) Palette.BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Close sidebar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Small uppercase group label with a trailing affordance. */
@Composable
private fun GroupLabel(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Rounded pill text field. `BasicTextField` has no placeholder or container of its own, so
 * both are drawn here — and `onFocusChanged` reports focus upwards for the back handling.
 */
@Composable
private fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChange(it.isFocused) },
            )
        }
    }
}

/** One bordered group, the active segment filled — the pattern used for ranges. */
@Composable
private fun SegmentedSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.background
                        },
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(1.dp)
                            .height(38.dp)
                            .background(MaterialTheme.colorScheme.outline),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(Palette.BrandBlue)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Palette.LightSurface,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Palette.LightSurface,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun OutlineButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(21.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Footer: avatar + summary + overflow menu. */
@Composable
private fun AccountRow(state: DemoUiState, onReset: () -> Unit, onClose: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(AccentFor(state)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "T",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.LightSurface,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = state.displayName.ifBlank { "Not configured" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${state.section.label} · saved ${state.saveCount}× this session",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Reset everything") },
                    leadingIcon = { Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp)) },
                    onClick = {
                        menuOpen = false
                        onReset()
                    },
                )
                DropdownMenuItem(
                    text = { Text("About this template") },
                    leadingIcon = { Icon(Icons.Outlined.Info, null, Modifier.size(18.dp)) },
                    onClick = {
                        menuOpen = false
                        onClose()
                    },
                )
            }
        }
    }
}

private fun AccentFor(state: DemoUiState) = when {
    state.busy -> Palette.BrandBlue
    state.displayName.isNotBlank() -> Palette.Ok
    else -> Palette.LightTextTertiary
}

/** Tiny helper the content area reuses: a check mark row, e.g. for "what's included" lists. */
@Composable
fun CheckRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = Palette.Ok,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
