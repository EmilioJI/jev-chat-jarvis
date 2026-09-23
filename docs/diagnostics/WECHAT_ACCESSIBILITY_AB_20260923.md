# WeChat Accessibility A/B Diagnostic — 2026-09-23

## Scope

This is a debug-only investigation of why the historical JEV build could read
WeChat's visible chat tree while the current safe build receives one empty root.

No probe stores message text, contact names, content descriptions, or raw view IDs.
Only structural counts and package names are persisted.

## Historical boundary

Last pre-safety baseline:

- commit: `0bd8f68ad2153a94fd820da793b45b05330cc500`
- debug package: `com.jev.probe.guofeng`
- service component class:
  `com.google.android.accessibility.selecttospeak.SelectToSpeakService`
- service extends `ChatCaptureService`
- metadata:
  - `isAccessibilityTool=true`
  - `flagReportViewIds`
  - `flagRetrieveInteractiveWindows`
  - `flagIncludeNotImportantViews`
  - no `packageNames` filter
- historical WeChat parser matched `com.tencent.mm:id/bkl`.

The historical source comment says the system-style component identity exposed the
full WeChat chat tree where a plainly named service received an empty root.

## Emulator result

Existing AVD reused without downloading a new image:

- AVD: `XST_DOGFOOD_AUTOMATION_API35`
- Android 15 / API 35
- 1080 x 2400
- existing cached system image

WeChat 8.0.77 was extracted directly from the vivo phone and installed successfully,
but aborts during startup under Android Emulator ARM-to-x86 translation:

- `libndk_translation.so`
- WeChat `libowl.so`
- WeChat `libilink_stream.so`
- fatal `SIGABRT`

Therefore the emulator is valid for JEV build/profile mechanics but cannot serve as
the final WeChat tree-exposure oracle.

## Four-profile harness

Profiles:

- A — current strict metadata
- B — honest JEV identity + tool=true + include-not-important + WeChat filter
- C — honest JEV identity + historical open metadata
- D — historical SelectToSpeak component identity + same open metadata as C

Probe fields include total/text/description/editable/id/clickable node counts,
maximum depth, window counts, and the count of historical `bkl` bubble matches.

## Emulator harness validation

Pixel Launcher, same APK, no reinstall between profiles:

- A: 18 total nodes, 9 text nodes, 8 id nodes, depth 4
- B: 34 total nodes, 9 text nodes, 20 id nodes, depth 13
- C: 34 total nodes, 9 text nodes, 20 id nodes, depth 13
- D: 34 total nodes, 9 text nodes, 20 id nodes, depth 13

This proves the profile metadata is actually reloaded when switching services.

## vivo test 1 — side-by-side package

Package: `com.jev.probe.abprobe`.

WeChat A `LauncherUI`:

- A: 1 node, 0 text
- B: 1 node, 0 text
- C: 1 node, 0 text
- D: 1 node, 0 text

Because the application package differed from the historical build, this result was
not treated as conclusive for D.

## vivo test 2 — exact historical application identity

A second temporary build matched:

- applicationId: `com.jev.probe.guofeng`
- versionCode: 4
- versionName: `1.3-guofeng-debug`
- application label: 小书童·知言
- same debug signing certificate as the safe installed APK
- C/D historical open metadata
- D exact historical service component class

Certificate SHA-256 for both the safe APK and exact diagnostic APK:

`e92e4d40ce7694bce8d54dec2eb7e23afcd1a871891fe11e50cfec5ce57afa3f`

WeChat A `LauncherUI` still returned:

- A: 1 node, 0 text
- B: 1 node, 0 text
- C: 1 node, 0 text
- D: 1 node, 0 text

The historical identity alone therefore does not restore the tree on the current
WeChat main screen.

## Restoration integrity

After the exact-identity test, the safe APK was restored immediately.

- restored APK SHA-256:
  `C8B1E734D77F3814A7939DF4F73EB49BF969A238880962B542F5FB0AAB555E06`
- expected safe APK SHA-256: identical
- `jev_assistant.xml` SHA-256 before:
  `3c50c76d72fca9e3294a4cdda5aaf9b6ad48edc46764f4c369c28f6fc050ac86`
- after: identical
- original AccessibilityService component restored
- `accessibility_enabled=1`

No JEV preference data changed.

## Remaining decisive gate

The historical claim is specifically about a WeChat chat window. The final test is
therefore D on a real open chat / ChattingMainUI:

- if total/text/id nodes recover and/or `legacy_bkl_nodes > 0`, the old read path
  is still technically reproducible and can be minimized further;
- if D still returns one empty root, the historical workaround is not reproducible
  on the current vivo / Android 16 / WeChat 8.0.77 environment, and restoring the
  old production architecture would not recover the original capability.
