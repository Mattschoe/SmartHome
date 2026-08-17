package com.mattschoe.smarthome.ui.controls.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mattschoe.smarthome.data.model.TodoItem
import com.mattschoe.smarthome.ui.theme.Dimensions
import com.mattschoe.smarthome.ui.theme.Forest
import com.mattschoe.smarthome.ui.theme.Ink
import com.mattschoe.smarthome.ui.theme.Muted
import org.jetbrains.compose.resources.painterResource
import smarthome.shared.generated.resources.Res
import smarthome.shared.generated.resources.checkbox_blank
import smarthome.shared.generated.resources.checkbox_filled

/**
 * A todo row with two targets, the split every checklist uses: **the box toggles done, the label
 * opens the editor**. Editing was a long-press before, which a mouse has no comfortable equivalent
 * for — a plain tap on the label reads the same on the tablet and in the desktop window. Done rows
 * are struck through and muted; committing a blank label removes the item (the delete escape).
 *
 * The row prints no date: the Opgaver panel stacks its rows under a date header per day, so saying
 * it again on every row would only repeat the header down the whole group.
 */
@Composable
internal fun TodoRow(
    todo: TodoItem,
    onToggle: () -> Unit,
    onCommitEdit: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        TodoInlineEdit(
            initial = todo.label,
            checked = todo.done,
            onCommit = { text, _ -> editing = false; onCommitEdit(text) },
            onCancel = { editing = false },
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().height(Dimensions.minTouch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The glyph stays 24dp wide — anything wider would push the label out of line with the
            // agenda rows above — but its target fills the row's full height, so it is comfortably
            // hittable with a fingertip.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(onClick = onToggle)
                    .semantics { contentDescription = if (todo.done) "Fjern flueben" else "Sæt flueben" },
                contentAlignment = Alignment.Center,
            ) {
                CheckboxGlyph(checked = todo.done)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { editing = true },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = todo.label,
                    color = if (todo.done) Muted else Ink,
                    fontSize = 16.sp,
                    textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The ghost add row: a loose unchecked box + faint placeholder that opens an inline field on tap.
 * A keyboard commit reopens the field empty, so a list can be typed in one go (write, Enter, write,
 * Enter) — on the desktop window that is the whole point, and the soft keyboard stays up on touch.
 */
@Composable
internal fun AddTodoRow(onAdd: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    // Bumped per keyboard commit purely to remount the field with an empty value.
    var round by remember { mutableIntStateOf(0) }
    if (adding) {
        key(round) {
            TodoInlineEdit(
                initial = "",
                checked = false,
                // Empty commit discards without touching the backend; a non-empty one adds. Only a
                // keyboard commit means "and now the next one" — closing the row on focus loss keeps
                // a tap elsewhere from pulling focus straight back into it.
                onCommit = { text, fromKeyboard ->
                    if (text.isNotBlank()) onAdd(text)
                    if (fromKeyboard && text.isNotBlank()) round++ else adding = false
                },
                onCancel = { adding = false },
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimensions.minTouch)
                .clickable { adding = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CheckboxGlyph(checked = false)
            Text("Tilføj opgave", color = Muted, fontSize = 16.sp)
        }
    }
}

/**
 * Inline single-line editor shared by add + edit. Auto-focuses, commits on Enter / IME-Done or on
 * focus loss, abandons on Escape, and guards against a double commit (Done removes the field, which
 * then fires focus-loss too). [onCommit]'s second argument marks a keyboard commit — what lets the
 * add row stay open for the next item without a stray tap elsewhere doing the same.
 */
@Composable
private fun TodoInlineEdit(
    initial: String,
    checked: Boolean,
    onCommit: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var committed by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    fun commit(fromKeyboard: Boolean) {
        if (!committed) { committed = true; onCommit(value.text, fromKeyboard) }
    }
    fun cancel() { if (!committed) { committed = true; onCancel() } }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier.fillMaxWidth().height(Dimensions.minTouch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CheckboxGlyph(checked = checked)
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 16.sp),
            cursorBrush = SolidColor(Forest),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit(fromKeyboard = true) }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                // Previewed, so a hardware Enter commits the row instead of being eaten as input.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> { commit(fromKeyboard = true); true }
                        Key.Escape -> { cancel(); true }
                        else -> false
                    }
                }
                .onFocusChanged { state ->
                    if (state.isFocused) hadFocus = true else if (hadFocus) commit(fromKeyboard = false)
                },
        )
    }
}

@Composable
internal fun CheckboxGlyph(checked: Boolean, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(if (checked) Res.drawable.checkbox_filled else Res.drawable.checkbox_blank),
        contentDescription = null,
        tint = if (checked) Forest else Muted,
        modifier = modifier.size(24.dp),
    )
}
