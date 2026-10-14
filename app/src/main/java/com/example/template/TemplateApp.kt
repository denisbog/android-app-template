package com.example.template

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.template.data.DemoSection
import com.example.template.ui.components.AppearIn
import com.example.template.ui.components.AppSnackbarHost
import com.example.template.ui.components.CheckRow
import com.example.template.ui.components.SidebarDrawerHost
import com.example.template.ui.components.DemoSidebarContent
import com.example.template.ui.components.rememberSidebarDrawerState
import com.example.template.ui.icons.DrawerIcons
import com.example.template.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * The app shell: a sidebar in a custom drawer on the left, a top bar and a scrollable content
 * area on the right. Everything the demo shows lives in these two composables plus
 * [DemoViewModel] — copy the file, rename, and start replacing the content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateApp(viewModel: DemoViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberSidebarDrawerState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    // On a wide screen the sidebar is docked, so the overlay trigger is hidden.
    val wideScreen = LocalConfiguration.current.screenWidthDp >= 600

    var sectionMenuOpen by remember { mutableStateOf(false) }
    // Reported by the sidebar so the drawer knows whether BACK belongs to the keyboard.
    var fieldFocused by remember { mutableStateOf(false) }

    // One-shot messages (see DemoViewModel.consumeMessage).
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    SidebarDrawerHost(
        state = drawerState,
        editing = fieldFocused,
        onBackWhileEditing = { keyboard?.hide() },
        drawerContent = {
            DemoSidebarContent(
                state = state,
                onDisplayNameChange = viewModel::onDisplayNameChange,
                onSection = viewModel::selectSection,
                onDenseRows = viewModel::setDenseRows,
                onSave = {
                    keyboard?.hide()
                    viewModel.save()
                    // A phone cannot show the content and the panel at once; close on save.
                    if (!wideScreen && state.displayName.isNotBlank()) {
                        scope.launch { drawerState.close() }
                    }
                },
                onClear = viewModel::clear,
                onWork = viewModel::simulateWork,
                onReset = viewModel::reset,
                onClose = { scope.launch { drawerState.close() } },
                onFieldFocus = { fieldFocused = it },
            )
        },
        content = {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { AppSnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = state.displayName.ifBlank { "App Template" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            if (!wideScreen) {
                                IconButton(onClick = {
                                    scope.launch {
                                        if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                    }
                                }) {
                                    Icon(DrawerIcons.MenuIcon, contentDescription = "Open sidebar")
                                }
                            }
                        },
                        actions = {
                            SectionChip(
                                section = state.section,
                                expanded = sectionMenuOpen,
                                onExpandedChange = { sectionMenuOpen = it },
                                onSelect = {
                                    sectionMenuOpen = false
                                    viewModel.selectSection(it)
                                },
                            )
                            IconButton(onClick = viewModel::simulateWork) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = "Simulate work")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    )
                },
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (state.denseRows) 8.dp else 14.dp),
                ) {
                    item(key = "status") { StatusLine(state = state) }
                    items(
                        items = state.section.cards(),
                        key = { it.title },
                    ) { card ->
                        AppearIn {
                            InfoCard(
                                title = card.title,
                                body = card.body,
                                bullets = card.bullets,
                                dense = state.denseRows,
                            )
                        }
                    }
                    item(key = "tap-me") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { viewModel.simulateWork() }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = Palette.BrandBlue,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Tap for a snackbar — the one-shot message pattern",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}

/** What the content area shows for the selected section. */
private data class InfoCardModel(
    val title: String,
    val body: String,
    val bullets: List<String> = emptyList(),
)

private fun DemoSection.cards(): List<InfoCardModel> = when (this) {
    DemoSection.Overview -> listOf(
        InfoCardModel(
            title = "What this is",
            body = "A running Compose app whose only job is to carry the patterns that are " +
                "tedious to re-derive: a custom drawer with real gestures, a sidebar with a " +
                "consistent vocabulary, persisted settings, and scripts that build, install, " +
                "screenshot and release.",
        ),
        InfoCardModel(
            title = "What is included",
            bullets = listOf(
                "ui/components/SidebarDrawer.kt — modal drawer, docked sidebar ≥ 600 dp",
                "ui/components/Sidebar.kt — header, pill field, segmented selector, rows, footer",
                "ui/theme — palette, type scale, Material 3 colour scheme (dark + light)",
                "data/SettingsStore.kt + DemoViewModel — one owner for persisted state",
                "build-release.sh / test.sh / run.sh / ui.sh — build, test and drive the app",
            ),
            body = "Every file is meant to be edited, not imported.",
        ),
        InfoCardModel(
            title = "Before you ship",
            body = "Change the application id and package (see the README checklist), replace the " +
                "launcher icon, and read docs/PITFALLS.md once — it lists the traps this " +
                "template already avoids.",
        ),
    )

    DemoSection.Sidebar -> listOf(
        InfoCardModel(
            title = "The sidebar vocabulary",
            body = "Header (avatar, title, subtitle, close) · group labels · pill text field · " +
                "segmented selector · switch row · action rows · account-row footer with an " +
                "overflow menu. That is all a settings panel usually needs.",
        ),
        InfoCardModel(
            title = "Try it",
            bullets = listOf(
                "Type a name, tap Save: it persists and the header avatar picks up the initial",
                "Pick a section: the chip in the top bar follows",
                "Toggle Dense rows: the content spacing changes",
                "Check the footer: it shows the persisted value and this session's save count",
            ),
            body = "Kill the app and relaunch — the name, section and switch are all restored.",
        ),
        InfoCardModel(
            title = "Where the state lives",
            body = "SettingsStore owns the SharedPreferences file, DemoViewModel owns the " +
                "in-memory snapshot, and the composables are stateless. Swapping " +
                "SharedPreferences for DataStore or Room touches one file.",
        ),
    )

    DemoSection.Gestures -> listOf(
        InfoCardModel(
            title = "Opening the sidebar",
            bullets = listOf(
                "Tap the two-bar glyph in the top bar (hidden when the sidebar is docked)",
                "Drag right from the left edge — the strip is 44 dp and excluded from the system back gesture",
                "A short pull (≈ a fifth of the panel) is enough; a downward drag from the edge still scrolls",
            ),
            body = "All three work on a phone; on a ≥ 600 dp screen the sidebar is simply there.",
        ),
        InfoCardModel(
            title = "Closing it",
            bullets = listOf(
                "Tap the scrim, drag the panel left, or press back",
                "While a text field in the panel has focus, back only dismisses the keyboard",
                "Predictive back (Android 13+) follows the finger and restores the panel on cancel",
            ),
            body = "The panel follows the finger at fraction 0..1 and snaps on distance or fling.",
        ),
        InfoCardModel(
            title = "Why it is written this way",
            body = "The four non-obvious details — the edge drag on the content wrapper, the " +
                "exclusion-only strip, the low snap thresholds and the settle call outside the " +
                "pointer scope — are each a bug that was hit and fixed. docs/PITFALLS.md " +
                "explains them.",
        ),
    )
}

@Composable
private fun StatusLine(state: DemoUiState) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "App Template ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (state.busy) {
                "Working…"
            } else {
                "${state.section.label}: ${state.section.blurb}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (state.busy) Palette.BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
    bullets: List<String>,
    dense: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp),
            )
            .padding(if (dense) 12.dp else 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (bullets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            bullets.forEach { bullet ->
                CheckRow(text = bullet, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}

/** The actions-slot chip: current section, tap for the others. */
@Composable
private fun SectionChip(
    section: DemoSection,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (DemoSection) -> Unit,
) {
    Box {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable { onExpandedChange(true) }
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DemoSection.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.label) },
                    onClick = { onSelect(candidate) },
                )
            }
        }
    }
}
