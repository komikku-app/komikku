# presentation-widget/ Module

Home-screen Glance widget showing recent manga updates. Package root: `tachiyomi.presentation.widget.*`.

## Key classes

| Class | Purpose |
|-------|---------|
| `BaseUpdatesGridGlanceWidget` | Base Glance widget: loads update covers via Coil, renders grid |
| `UpdatesGridGlanceWidget` | Standard updates grid widget variant |
| `UpdatesGridCoverScreenGlanceWidget` | Cover screen variant with bottom padding |
| `UpdatesGridGlanceReceiver` | GlanceAppWidgetReceiver for standard widget |
| `UpdatesGridCoverScreenGlanceReceiver` | GlanceAppWidgetReceiver for cover screen variant |
| `WidgetManager` | Watches `GetUpdates` flow, triggers `updateAll()` on changes |
| `LockedWidget` | Widget shown when app is locked |
| `UpdatesMangaCover` | Individual manga cover in widget grid |
| `GlanceUtils` | Glance utility extensions |

## Architecture

- Uses Jetpack Glance (not traditional `AppWidgetProvider`)
- Two widget variants share `BaseUpdatesGridGlanceWidget` base class
- `WidgetManager` is a singleton that observes `GetUpdates` interactor flow
- Widget refresh triggers on: update flow changes, lock state changes
- Covers loaded via Coil 3 (`ImageLoader` from `App`)

## Dependencies

- `core:common` (ImageUtil, preferences)
- `domain` (GetUpdates interactor)
- `presentation-core` (shared components)
- Glance library (`androidx.glance:glance-*`)

## Conventions

- Package root: `tachiyomi.presentation.widget.*`
- Widget updates are triggered reactively (not polling)
- No fork markers (shared infrastructure)
