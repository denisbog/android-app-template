# Pitfalls

Every entry below is a bug that was actually hit while building this template. Each one says
what you *see*, why it happens, and what the code does about it. Read the drawer section before touching `SidebarDrawer.kt`; read the emulator section
before concluding that a script is broken.

---

## 1. Drawer and gestures

### 1.1 An overlay strip on top of the content swallows taps

**Symptom.** The drawer's edge-swipe works, but the menu button in the top bar stops
responding — taps in the left ~44 dp of the screen do nothing at all. Reducing the strip to
1 dp makes the button work again.

**Why.** The obvious implementation is a thin `Box` with a `draggable` on it, added as the
*last* child of the drawer's root `Box` so it sits above the content. Siblings are
hit-tested in reverse drawing order, so that strip is the topmost node for every touch in
its bounds — including the ones meant for the button underneath.

**Fix (in this template).** The edge detector is a `pointerInput` on the **content
wrapper**, not a sibling. A parent is dispatched *after* its children, so the button sees
the tap first and consumes it, while a drag that starts inside the edge zone still becomes
the drawer's gesture. The low-level loop lives in `dragDrawerFromEdge()`.

### 1.2 Under gesture navigation the OS eats the inward swipe

**Symptom.** Dragging right from the very left edge (x ≈ 0–20 px) never opens the drawer,
although the same drag starting 100 px in works. On a device in gesture navigation the
swipe is sometimes interpreted as *back* instead.

**Why.** With `navigation_mode=2` the system claims both screen edges for the back gesture.
The platform only backs off where the app declares an exclusion.

**Fix.** An invisible strip along the left edge whose only job is
`Modifier.systemGestureExclusion()`:

```kotlin
Box(Modifier.width(EdgeDragWidth).fillMaxHeight().systemGestureExclusion())
```

Three details matter: it must carry **no** pointer input (see 1.1), it must be *under* the
content (it is the first child), and the exclusion only applies while the drawer is closed
(no reason to keep stealing the back gesture once it is open).

### 1.3 Material's snap thresholds make a short pull feel broken

**Symptom.** You drag the drawer a third of the way across the screen, let go, and it snaps
back closed. "The swipe doesn't work" — but a fast fling does.

**Why.** Drawer implementations commonly settle with `fraction > 0.45f || velocity > 900f`.
On a 320 dp panel that is a 145 dp pull before anything happens.

**Fix.** `SidebarDrawerState.settle()` uses `0.22f` and `450f`: a fifth of the panel width, or a
gentle fling, opens it. Closing is symmetric, so it does not become sticky.

### 1.4 `AwaitPointerEventScope` is a restricted suspend scope

**Symptom.** Compile error: *"Restricted suspending functions can invoke member or
extension suspending functions only on their restricted coroutine scope"*, pointing at a
call like `state.settle(velocity)` inside `pointerInput { awaitEachGesture { … } }`.

**Why.** Inside `awaitEachGesture`/`awaitPointerEventScope` you may only call suspend
functions of that scope. A plain `suspend fun` on your own class is not one of them.

**Fix.** Return the value you need from the restricted block and act on it outside:

```kotlin
Modifier.pointerInput(keys) {
    while (true) {
        val velocity = awaitPointerEventScope { dragDrawerFromEdge(state, edgePx) }
        if (velocity != null) state.settle(velocity)   // regular suspend scope — allowed
    }
}
```

### 1.5 Do not remove the pointer node while the drawer is animating

**Symptom.** The open animation never runs: the panel stops dead at whatever fraction the
finger left it at, and sometimes stays half-open.

**Why.** `settle()` sets `isOpen = true` *before* it animates. If the gesture modifier was
keyed on `state.isOpen`, that assignment triggers recomposition, the node is removed, its
coroutine is cancelled, and the animation is cancelled with it.

**Fix.** Key the `pointerInput` on something stable (`gesturesEnabled`) and check
`state.isOpen` *inside* the loop, returning early when it is already open.

### 1.6 Back must not fall through to the activity

**Symptom.** With the keyboard up and a text field in the drawer focused, one BACK press
hides the keyboard **and** finishes the activity — the app disappears instead of just
closing the panel or the keyboard.

**Why.** On many devices a single injected/real BACK is delivered as a down to the IME
(hides it) and an up to the app. If no back handler is enabled at that moment, the up
reaches the system's "finish activity" path.

**Fix.** Keep a `BackHandler` enabled whenever the drawer is open and branch inside it:

```kotlin
BackHandler(enabled = state.isOpen) {
    if (editing) onBackWhileEditing() else scope.launch { state.close() }
}
```

`editing` comes from the sidebar's text field via `onFocusChanged` → `onFieldFocus`. Note
`ImeVisible` is *not* reliable for this (see 3.2), so focus is tracked explicitly.

---

## 2. Insets

### 2.1 The sidebar's first row sits under the status bar

**Symptom.** With `enableEdgeToEdge()`, the top of the drawer panel is behind the clock and
battery — the first control is partly unreadable.

**Why.** The panel is drawn edge to edge by design (that is what makes it look like a
native drawer), and `Scaffold`'s insets only apply to the scaffold's own content.

**Fix (in this template).** The sidebar root applies the inset itself:

```kotlin
Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars))
```

### 2.2 `adjustResize` no longer shrinks the window on API 35+

**Symptom.** The IME covers the bottom of the layout; a "Send"/"Save" button placed at the
bottom is unreachable with the keyboard up.

**Why.** Since API 35, `android:windowSoftInputMode="adjustResize"` no longer resizes the
window when the app is edge-to-edge — the IME simply draws over it.

**Fix.** Do not rely on it: put actions *above* the keyboard height (this template's
`Save`/`Clear` are near the top of the panel), use `Modifier.imePadding()` where a
bottom-anchored control is unavoidable, or hide the keyboard before acting (the scripts do
the latter). The manifest in this template still declares `adjustResize` because it helps on
older releases.

---

## 3. Compose details

### 3.1 A local `Density` shadows `graphicsLayer`'s `density`

**Symptom.** After adding `val density = LocalDensity.current` near a
`Modifier.graphicsLayer { … }`, the build fails with *"None of the following candidates is
applicable"* on `cameraDistance = 14f * density`.

**Why.** Inside `graphicsLayer`'s lambda, `density` is a `Float` of the layer scope. A local
`Density` object with the same name wins, and `Float * Density` does not exist.

**Fix.** Do not name the local variable `density` (this template converts dp straight into
px with `with(LocalDensity.current) { … }`), or use `this@graphicsLayer.density`.

### 3.2 `WindowInsets.isImeVisible` can be false while the keyboard is up

**Symptom.** Gestures that are supposed to be disabled while the keyboard is visible (to
avoid fighting the IME) stay enabled, or the opposite: BACK handling that relies on
`imeVisible` never triggers. `adb shell dumpsys input_method` says `mInputShown=true`.

**Why.** The inset is reported per window and depends on the soft-input mode and the
emulator image; it is not a reliable "is the user typing" signal.

**Fix.** Track focus of your own text fields (`Modifier.onFocusChanged`, reported up with a
callback) and use that as the source of truth for both the drag gestures and BACK.

### 3.3 `BasicTextField` has no placeholder and no container

**Symptom.** The field is invisible until you type into it, or the placeholder stays visible
behind the text.

**Fix.** Draw both yourself: a `Box` with `if (value.isEmpty()) Text(placeholder)` and the
`BasicTextField` on top, inside a `Row` with the pill background. See `PillField` in
`Sidebar.kt`.

### 3.4 Data classes holding `List`s are unstable

**Symptom.** Everything recomposes on every state change once a model contains a `List`.

**Why.** Compose's stability inference treats `List` as unstable unless you opt in
(`kotlinx.collections.immutable` or `@Immutable`).

**Fix.** Keep the models small, and keep expensive work (canvas drawing, layout maths) out
of recomposition-heavy paths — or annotate the class. For a settings sidebar it does not
matter; for a long list it does.

### 3.5 `maxOf(Int, Float)` does not compile

**Symptom.** *"None of the following candidates is applicable"* when mixing
`TextLayoutResult.size.width` (Int) with `dp.toPx()` (Float).

**Fix.** Convert explicitly: `maxOf(a.size.width.toFloat(), b + pad)`.

### 3.6 `/*` inside a KDoc comment is a nested comment

**Symptom.** *"Syntax error: Unclosed comment"* on the last line of a file, with nothing
obviously wrong — the file only contains a doc comment.

**Why.** Kotlin block comments **nest**, so writing a route like `` `/api/*` `` inside a
`/** … */` comment opens a nested comment that is never closed.

**Fix.** Write `` `/api/` `` (or `` `/api/…` ``) inside comments. This bites in API docs
constantly.

### 3.7 `graphicsLayer` transforms do not move the hit area

**Symptom.** A panel translated off-screen with `graphicsLayer { translationX = … }` still
responds to touches where it *used* to be — a closed drawer keeps eating gestures in its old
place.

**Fix.** Remember that layout bounds stay put; guard the hidden state explicitly (this
template removes the edge strip while the drawer is open and checks `state.isOpen` in the
drag loop).

### 3.8 Material's default snackbar ignores your theme

**Symptom.** A snackbar shows up as a light grey, square-cornered slab in an otherwise dark,
rounded, hairline-bordered app — it reads as a system banner rather than as part of the app.

**Why.** `Snackbar` paints `colorScheme.inverseSurface`, and `SnackbarHost` adds no padding of
its own. That contrast is deliberate against a *Material* colour scheme, not against a
hand-built one.

**Fix.** Give the host your own content — `SnackbarHost(state) { AppSnackbar(it) }` — and build
the bar from the same parts as the rest of the screen: the card radius and hairline, the
`surfaceContainer` fill and a brand dot, inset to line up with the content. See
`ui/components/AppSnackbar.kt` (`AppSnackbarHost` wraps exactly this).

---

## 4. Emulator and scripts

### 4.1 `input text` needs `%s` for spaces

**Symptom.** `adb shell input text "hello world"` types `hello` and drops the rest.

**Fix.** Escape spaces (`%s`) and shell metacharacters — `type_text()` in `ui.sh` does it.

### 4.2 Clearing a text field with Ctrl+A silently fails

**Symptom.** A helper that "clears then types" leaves `http://old-urlhttps://new-url` in the
field, and the app then fails in a way that looks like a bug in the app. `input
keycombination 113 29` (Ctrl+A) does not reach Compose fields on every image.

**Fix.** `MOVE_END` (keyevent 123) followed by a generous number of `DELETE` (keyevent 67) —
`clear_focused_field()` in `ui.sh`. Then verify the dumped field text if it matters.

### 4.3 `input swipe` only returns when the gesture is over

**Symptom.** Screenshots of pressed states, crosshairs or half-open drawers always show the
idle state.

**Fix.** Run the swipe in the background and capture from a second process —
`./ui.sh drag-shot <file> <x1> <y1> <x2> <y2>`.

### 4.4 Installing a release APK over a debug build fails

**Symptom.** `adb install -r` reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package … does
not match the previously installed version` (or `… signatures do not match previously
installed version`).

**Why.** Debug and release are signed with different keys; Android refuses to upgrade across
keys. Re-running the same install never helps.

**Fix.** Uninstall once, then install. `test.sh` detects the failure, uninstalls and retries;
`build-release.sh --install` does the same.

### 4.5 `am start` on an already-running top activity does nothing

**Symptom.** *"Warning: Activity not started, intent has been delivered to currently running
top-most instance."* — the app keeps its old state, so your "fresh launch" test is not
fresh.

**Fix.** `adb shell am force-stop <app-id>` first (`./test.sh --cold`), or
`am start -S`/`--activity-clear-top`. Note that force-stopping keeps
`SharedPreferences`, so it is the right way to test persistence — but not a fresh install;
`adb uninstall` for that.

### 4.6 The emulator window can be off-screen (but screenshots look fine)

**Symptom.** You cannot see or click the emulator, yet `adb exec-out screencap` produces
perfect images: the window restored its position from `emulator-user.ini` to coordinates
outside the desktop.

**Fix.** Move or delete the saved position, or start the emulator with an explicit
`-scale`/window position. `run.sh` is the windowed entry point for this reason; if you use
`run.sh`, click the emulator window once before typing — under Wayland the compositor routes
keys to whichever window is focused, and they otherwise go to your terminal.

### 4.7 The accessibility tree is the interface, not the pixels

**Symptom.** Coordinate-based taps break every time a layout shifts.

**Fix.** Give interactive elements text or a `contentDescription` and drive them by name
(`./ui.sh tap "Open sidebar"`). `./ui.sh here` prints everything on screen with its centre,
which doubles as a cheap layout assertion in tests.

---

## 5. Build and packaging

### 5.1 Signing material is the app's identity

Losing `release.keystore` means never being able to update the installed app; leaking it
means anyone can ship an update for it. Both files are git-ignored here and generated `600`
by `build-release.sh`. Back them up somewhere safe, and keep them out of CI logs.

### 5.2 Verify the signature instead of trusting "release"

`build-release.sh` runs `apksigner verify --print-certs`. An APK that was not actually
signed still installs over adb on some devices, which hides the problem until a user cannot
install an update.

### 5.3 `BuildConfig` is not generated unless you ask

In AGP 8 `android.buildFeatures.buildConfig` defaults to false, so `BuildConfig.VERSION_NAME`
does not resolve until you enable it (this template does — the demo shows the version).

### 5.4 Keep Kotlin and the Compose compiler plugin in step

Since Kotlin 2.0 the Compose compiler ships with Kotlin; `org.jetbrains.kotlin.plugin.compose`
must have exactly the Kotlin version. A mismatch fails with an unhelpful message.

### 5.5 R8 only breaks at runtime

Turning on `isMinifyEnabled`/`isShrinkResources` (left off here for readability) is only
validated by installing the **release** build on a device and exercising it — reflection,
serialization and Compose previews can all fail in ways the debug build never shows.
