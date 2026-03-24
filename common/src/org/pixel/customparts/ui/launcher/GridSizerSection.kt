package org.pixel.customparts.ui.launcher

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import org.pixel.customparts.activities.LauncherManager
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.RadioSelectionGroup
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.WeakDivider
import org.pixel.customparts.ui.StrongDivider
import org.pixel.customparts.ui.ExpandableSettingsGroupCard
import org.pixel.customparts.utils.SettingsCompat
import org.pixel.customparts.utils.dynamicStringResource

@Composable
fun GridSizerSection(
    context: Context,
    scope: CoroutineScope,
    restartTrigger: Int,
    onSettingsChanged: (Boolean) -> Unit
) {
    // Keys
    val keyHomeEnable = LauncherManager.KEY_HOME_ENABLE
    val keyHomeCols = LauncherManager.KEY_HOME_COLS
    val keyHomeRows = LauncherManager.KEY_HOME_ROWS
    val keyHomeIconSize = LauncherManager.KEY_HOME_ICON_SIZE
    val keyHomeTextMode = LauncherManager.KEY_HOME_TEXT_MODE
    val keyDockIcons = LauncherManager.KEY_HOTSEAT_ICONS
    val keyDockIconSize = LauncherManager.KEY_HOTSEAT_ICON_SIZE
    val keyMenuEnable = LauncherManager.KEY_MENU_ENABLE
    val keyMenuCols = LauncherManager.KEY_MENU_COLS
    val keyMenuRowHeight = LauncherManager.KEY_MENU_ROW_HEIGHT
    val keyMenuIconSize = LauncherManager.KEY_MENU_ICON_SIZE
    val keyMenuTextMode = LauncherManager.KEY_MENU_TEXT_MODE
    val keySuggestionIconSize = "launcher_suggestion_icon_size"
    val keySuggestionTextMode = "launcher_suggestion_text_mode"
    val keySearchIconSize = "launcher_search_icon_size"
    val keySearchTextMode = "launcher_search_text_mode"
    val keySuggestionDisable = "launcher_suggestion_disable"

    val initHomeEnabled = remember { SettingsCompat.getInt(context, keyHomeEnable, 0) == 1 }
    var homeEnabled by remember { mutableStateOf(initHomeEnabled) }
    var baselineHomeEnabled by remember { mutableStateOf(initHomeEnabled) }

    val initDockIcons = remember { SettingsCompat.getInt(context, keyDockIcons, 4) }
    var dockIcons by remember { mutableIntStateOf(initDockIcons) }
    var baselineDockIcons by remember { mutableIntStateOf(initDockIcons) }

    val initDockIconSize = remember { SettingsCompat.getInt(context, keyDockIconSize, 100) }
    var dockIconSize by remember { mutableIntStateOf(initDockIconSize) }
    var baselineDockIconSize by remember { mutableIntStateOf(initDockIconSize) }

    val initHomeCols = remember { SettingsCompat.getInt(context, keyHomeCols, 4) }
    var homeCols by remember { mutableIntStateOf(initHomeCols) }
    var baselineHomeCols by remember { mutableIntStateOf(initHomeCols) }

    val initHomeRows = remember { SettingsCompat.getInt(context, keyHomeRows, 6) }
    var homeRows by remember { mutableIntStateOf(initHomeRows) }
    var baselineHomeRows by remember { mutableIntStateOf(initHomeRows) }

    val initHomeIconSize = remember { SettingsCompat.getInt(context, keyHomeIconSize, 100) }
    var homeIconSize by remember { mutableIntStateOf(initHomeIconSize) }
    var baselineHomeIconSize by remember { mutableIntStateOf(initHomeIconSize) }

    val initHomeTextMode = remember { SettingsCompat.getInt(context, keyHomeTextMode, 0) }
    var homeTextMode by remember { mutableIntStateOf(initHomeTextMode) }
    var baselineHomeTextMode by remember { mutableIntStateOf(initHomeTextMode) }

    val initMenuEnabled = remember { SettingsCompat.getInt(context, keyMenuEnable, 0) == 1 }
    var menuEnabled by remember { mutableStateOf(initMenuEnabled) }
    var baselineMenuEnabled by remember { mutableStateOf(initMenuEnabled) }

    val initMenuCols = remember { SettingsCompat.getInt(context, keyMenuCols, 4) }
    var menuCols by remember { mutableIntStateOf(initMenuCols) }
    var baselineMenuCols by remember { mutableIntStateOf(initMenuCols) }

    val initMenuRowHeight = remember { SettingsCompat.getInt(context, keyMenuRowHeight, 100) }
    var menuRowHeight by remember { mutableIntStateOf(initMenuRowHeight) }
    var baselineMenuRowHeight by remember { mutableIntStateOf(initMenuRowHeight) }

    val initMenuIconSize = remember { SettingsCompat.getInt(context, keyMenuIconSize, 100) }
    var menuIconSize by remember { mutableIntStateOf(initMenuIconSize) }
    var baselineMenuIconSize by remember { mutableIntStateOf(initMenuIconSize) }

    val initMenuTextMode = remember { SettingsCompat.getInt(context, keyMenuTextMode, 0) }
    var menuTextMode by remember { mutableIntStateOf(initMenuTextMode) }
    var baselineMenuTextMode by remember { mutableIntStateOf(initMenuTextMode) }

    val initSuggestionIconSize = remember { SettingsCompat.getInt(context, keySuggestionIconSize, 100) }
    var suggestionIconSize by remember { mutableIntStateOf(initSuggestionIconSize) }
    var baselineSuggestionIconSize by remember { mutableIntStateOf(initSuggestionIconSize) }

    val initSuggestionTextMode = remember { SettingsCompat.getInt(context, keySuggestionTextMode, 0) }
    var suggestionTextMode by remember { mutableIntStateOf(initSuggestionTextMode) }
    var baselineSuggestionTextMode by remember { mutableIntStateOf(initSuggestionTextMode) }

    val initSuggestionDisable = remember { SettingsCompat.getInt(context, keySuggestionDisable, 0) == 1 }
    var suggestionDisabled by remember { mutableStateOf(initSuggestionDisable) }
    var baselineSuggestionDisabled by remember { mutableStateOf(initSuggestionDisable) }

    val initSearchIconSize = remember { SettingsCompat.getInt(context, keySearchIconSize, 100) }
    var searchIconSize by remember { mutableIntStateOf(initSearchIconSize) }
    var baselineSearchIconSize by remember { mutableIntStateOf(initSearchIconSize) }

    val initSearchTextMode = remember { SettingsCompat.getInt(context, keySearchTextMode, 0) }
    var searchTextMode by remember { mutableIntStateOf(initSearchTextMode) }
    var baselineSearchTextMode by remember { mutableIntStateOf(initSearchTextMode) }

    val isModified by remember {
        derivedStateOf {
            homeEnabled != baselineHomeEnabled ||
                homeCols != baselineHomeCols ||
                homeRows != baselineHomeRows ||
                homeIconSize != baselineHomeIconSize ||
                homeTextMode != baselineHomeTextMode ||
                dockIcons != baselineDockIcons ||
                dockIconSize != baselineDockIconSize ||
                menuEnabled != baselineMenuEnabled ||
                menuCols != baselineMenuCols ||
                menuRowHeight != baselineMenuRowHeight ||
                menuIconSize != baselineMenuIconSize ||
                menuTextMode != baselineMenuTextMode ||
                suggestionIconSize != baselineSuggestionIconSize ||
                suggestionTextMode != baselineSuggestionTextMode ||
                suggestionDisabled != baselineSuggestionDisabled ||
                searchIconSize != baselineSearchIconSize ||
                searchTextMode != baselineSearchTextMode
        }
    }

    LaunchedEffect(isModified) {
        onSettingsChanged(isModified)
    }

    LaunchedEffect(restartTrigger) {
        baselineHomeEnabled = homeEnabled
        baselineHomeCols = homeCols
        baselineHomeRows = homeRows
        baselineHomeIconSize = homeIconSize
        baselineHomeTextMode = homeTextMode
        baselineDockIcons = dockIcons
        baselineDockIconSize = dockIconSize
        baselineMenuEnabled = menuEnabled
        baselineMenuCols = menuCols
        baselineMenuRowHeight = menuRowHeight
        baselineMenuIconSize = menuIconSize
        baselineMenuTextMode = menuTextMode
        baselineSuggestionIconSize = suggestionIconSize
        baselineSuggestionTextMode = suggestionTextMode
        baselineSuggestionDisabled = suggestionDisabled
        baselineSearchIconSize = searchIconSize
        baselineSearchTextMode = searchTextMode
    }


    // ... existing initialization code ...
    
    val textModeOptions = listOf(
        dynamicStringResource(R.string.grid_text_mode_default),
        dynamicStringResource(R.string.grid_text_mode_two_line),
        dynamicStringResource(R.string.grid_text_mode_marquee),
        dynamicStringResource(R.string.grid_text_mode_hide)
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGroupCard(
            title = dynamicStringResource(R.string.grid_homepage_title)
        ) {
            SliderSetting(
                title = dynamicStringResource(R.string.grid_lbl_dock_icons),
                value = dockIcons,
                    range = 1..12,
                    unit = "",
                    enabled = true,
                    valueText = "$dockIcons",
                    onValueChange = {
                        dockIcons = it
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyDockIcons, it) }
                    },
                    onDefault = {
                        dockIcons = 4
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyDockIcons, 4) }
                    },
                    infoText = dynamicStringResource(R.string.grid_desc_dock_icons),
                    onInfoClick = null
                )

                WeakDivider()

                SliderSetting(
                    title = dynamicStringResource(R.string.grid_lbl_icon_size),
                    value = dockIconSize,
                    range = 1..200,
                    unit = "%",
                    enabled = true,
                    onValueChange = {
                        dockIconSize = it
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyDockIconSize, it) }
                    },
                    onDefault = {
                        dockIconSize = 100
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyDockIconSize, 100) }
                    },
                    infoText = dynamicStringResource(R.string.grid_desc_dock_icon_size),
                    onInfoClick = null
                )

                StrongDivider()

                GenericSwitchRow(
                    title = dynamicStringResource(R.string.grid_lbl_home_enable),
                    checked = homeEnabled,
                    onCheckedChange = { checked ->
                        homeEnabled = checked
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyHomeEnable, if (checked) 1 else 0) }
                    }
                )

                WeakDivider()

                SliderSetting(
                    title = dynamicStringResource(R.string.grid_lbl_columns),
                    value = homeCols,
                    range = 4..12,
                    unit = "",
                    enabled = homeEnabled,
                    onValueChange = {
                        homeCols = it
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyHomeCols, it) }
                    },
                    onDefault = {
                        homeCols = 4
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyHomeCols, 4) }
                    },
                    infoText = dynamicStringResource(R.string.grid_desc_home_cols),
                    onInfoClick = null
                )

                WeakDivider()

                SliderSetting(
                    title = dynamicStringResource(R.string.grid_lbl_rows),
                    value = homeRows,
                    range = 1..16,
                    unit = "",
                    enabled = homeEnabled,
                    onValueChange = {
                        homeRows = it
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyHomeRows, it) }
                    },
                    onDefault = {
                        homeRows = 6
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyHomeRows, 6) }
                    },
                    infoText = dynamicStringResource(R.string.grid_desc_home_rows),
                    onInfoClick = null
                )

                WeakDivider()

                SliderSetting(
                    title = dynamicStringResource(R.string.grid_lbl_icon_size),
                    value = homeIconSize,
                    range = 1..200,
                    unit = "%",
                    enabled = homeEnabled,
                    onValueChange = {
                        homeIconSize = it
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyHomeIconSize, it) }
                    },
                    onDefault = {
                        homeIconSize = 100
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyHomeIconSize, 100) }
                    },
                    infoText = dynamicStringResource(R.string.grid_desc_home_icon_size),
                    onInfoClick = null
                )

                WeakDivider()

                RadioSelectionGroup(
                    title = dynamicStringResource(R.string.grid_lbl_text_mode),
                    options = textModeOptions,
                    selectedIndex = homeTextMode.coerceIn(0, 3),
                    onSelect = { mode ->
                        homeTextMode = mode
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyHomeTextMode, mode) }
                    },
                    enabled = homeEnabled,
                    infoText = dynamicStringResource(R.string.grid_desc_text_mode),
                    onInfoClick = null
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsGroupCard(
            title = dynamicStringResource(R.string.grid_menupage_title)
        ) {
            GenericSwitchRow(
                title = dynamicStringResource(R.string.grid_menupage_enable),
                checked = menuEnabled,
                onCheckedChange = { checked ->
                    menuEnabled = checked
                    scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyMenuEnable, if (checked) 1 else 0) }
                }
            )

            WeakDivider()

            SliderSetting(
                title = dynamicStringResource(R.string.grid_lbl_drawer_cols),
                    value = menuCols,
                    range = 1..15,
                    unit = "",
                    enabled = menuEnabled,
                    onValueChange = {
                        menuCols = it
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyMenuCols, it) }
                    },
                    onDefault = {
                        menuCols = 4
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyMenuCols, 4) }
                    },
                    infoText = dynamicStringResource(R.string.grid_desc_drawer_cols),
                    onInfoClick = null
                )

                WeakDivider()

                SliderSetting(
                    title = dynamicStringResource(R.string.grid_lbl_row_height),
                    value = menuRowHeight,
                    range = 30..700,
                    unit = "%",
                    enabled = menuEnabled,
                    onValueChange = {
                        menuRowHeight = it
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyMenuRowHeight, it) }
                    },
                    onDefault = {
                        menuRowHeight = 100
                        scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyMenuRowHeight, 100) }
                    },
                    infoText = dynamicStringResource(R.string.grid_desc_row_height),
                    onInfoClick = null
                )

                StrongDivider()

                var searchExpanded by remember { mutableStateOf(true) }
                ExpandableSettingsGroupCard(
                    title = dynamicStringResource(R.string.grid_menupage_search_title),
                    enabled = menuEnabled,
                    expanded = searchExpanded,
                    onExpandChange = { searchExpanded = it }
                ) {
                    SliderSetting(
                        title = dynamicStringResource(R.string.grid_lbl_icon_size),
                        value = searchIconSize,
                        range = 1..200,
                        unit = "%",
                        enabled = menuEnabled,
                        onValueChange = {
                            searchIconSize = it
                            scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keySearchIconSize, it) }
                        },
                        onDefault = {
                            searchIconSize = 100
                            scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keySearchIconSize, 100) }
                        },
                        infoText = dynamicStringResource(R.string.grid_desc_drawer_icon_size),
                        onInfoClick = null
                    )

                    WeakDivider()

                    RadioSelectionGroup(
                        title = dynamicStringResource(R.string.grid_lbl_text_mode),
                        options = textModeOptions,
                        selectedIndex = searchTextMode.coerceIn(0, 3),
                        onSelect = { mode ->
                            searchTextMode = mode
                            scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keySearchTextMode, mode) }
                        },
                        enabled = menuEnabled,
                        infoText = dynamicStringResource(R.string.grid_desc_text_mode),
                        onInfoClick = null
                    )
                }

                WeakDivider()

                var suggestionExpanded by remember { mutableStateOf(true) }
                ExpandableSettingsGroupCard(
                    title = dynamicStringResource(R.string.grid_menupage_suggestions_title),
                    enabled = menuEnabled,
                    expanded = suggestionExpanded,
                    onExpandChange = { suggestionExpanded = it }
                ) {
                    GenericSwitchRow(
                        title = dynamicStringResource(R.string.grid_menupage_suggestions_disable),
                        checked = suggestionDisabled,
                        enabled = menuEnabled,
                        onCheckedChange = { checked ->
                            suggestionDisabled = checked
                            scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keySuggestionDisable, if (checked) 1 else 0) }
                        }
                    )

                    val suggestionSettingsEnabled = menuEnabled && !suggestionDisabled

                    Box(modifier = Modifier.alpha(if (suggestionSettingsEnabled) 1f else 0.4f)) {
                        Column {
                            SliderSetting(
                                title = dynamicStringResource(R.string.grid_lbl_icon_size),
                                value = suggestionIconSize,
                                range = 1..200,
                                unit = "%",
                                enabled = suggestionSettingsEnabled,
                                onValueChange = {
                                    suggestionIconSize = it
                                    scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keySuggestionIconSize, it) }
                                },
                                onDefault = {
                                    suggestionIconSize = 100
                                    scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keySuggestionIconSize, 100) }
                                },
                                infoText = dynamicStringResource(R.string.grid_desc_drawer_icon_size),
                                onInfoClick = null
                            )

                            WeakDivider()

                            RadioSelectionGroup(
                                title = dynamicStringResource(R.string.grid_lbl_text_mode),
                                options = textModeOptions,
                                selectedIndex = suggestionTextMode.coerceIn(0, 3),
                                onSelect = { mode ->
                                    suggestionTextMode = mode
                                    scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keySuggestionTextMode, mode) }
                                },
                                enabled = suggestionSettingsEnabled,
                                infoText = dynamicStringResource(R.string.grid_desc_text_mode),
                                onInfoClick = null
                            )
                        }
                    }
                }

                WeakDivider()

                var menuExpanded by remember { mutableStateOf(true) }
                ExpandableSettingsGroupCard(
                    title = dynamicStringResource(R.string.grid_menupage_apps_title),
                    enabled = menuEnabled,
                    expanded = menuExpanded,
                    onExpandChange = { menuExpanded = it }
                ) {
                    SliderSetting(
                        title = dynamicStringResource(R.string.grid_lbl_icon_size),
                        value = menuIconSize,
                        range = 1..200,
                        unit = "%",
                        enabled = menuEnabled,
                        onValueChange = {
                            menuIconSize = it
                            scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyMenuIconSize, it) }
                        },
                        onDefault = {
                            menuIconSize = 100
                            scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyMenuIconSize, 100) }
                        },
                        infoText = dynamicStringResource(R.string.grid_desc_drawer_icon_size),
                        onInfoClick = null
                    )

                    WeakDivider()

                    RadioSelectionGroup(
                        title = dynamicStringResource(R.string.grid_lbl_text_mode),
                        options = textModeOptions,
                        selectedIndex = menuTextMode.coerceIn(0, 3),
                        onSelect = { mode ->
                            menuTextMode = mode
                            scope.launch(Dispatchers.IO) { SettingsCompat.putInt(context, keyMenuTextMode, mode) }
                        },
                        enabled = menuEnabled,
                        infoText = dynamicStringResource(R.string.grid_desc_text_mode),
                        onInfoClick = null
                    )
                }
            }
        }
    


