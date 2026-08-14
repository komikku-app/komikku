# presentation-core/ Module

Shared Compose components, theme system, and i18n helpers. Package root: `tachiyomi.presentation.core.*`.

## Key directories

| Path | Purpose |
|------|---------|
| `components/` | 17 reusable Compose components (ActionButton, AdaptiveSheet, Badges, etc.) |
| `components/material/` | 12 Material3 customizations (AlertDialog, Button, NavigationBar, PullRefresh, etc.) |
| `icons/` | `FlagEmoji.kt` (582 lines) - country flag emoji composables |
| `theme/` | Theme utilities (not the main color schemes - those are in `app/`) |
| `util/` | `PagingDataUtil.kt` - paging utilities |

## Components

| Component | Purpose |
|-----------|---------|
| `ActionButton` | Floating action button variant |
| `AdaptiveSheet` | Bottom sheet with adaptive layout |
| `Badges` | Badge composables for counts |
| `CircularProgressIndicator` | Loading indicator |
| `CollapsibleBox` | Expandable/collapsible container |
| `LabeledCheckbox` | Checkbox with label |
| `LazyColumnWithAction` | Lazy list with sticky action |
| `LazyGrid` / `LazyList` | Enhanced grid/list composables |
| `LinkIcon` | Icon with click handler |
| `ListGroupHeader` | Section header |

## Material customizations

Custom implementations of Material3 components:
- `AlertDialog`, `Button`, `IconButton`, `IconToggleButton`
- `NavigationBar`, `NavigationRail` (bottom/side nav)
- `PullRefresh` (swipe-to-refresh)
- `Constants.kt` - shared dimension tokens

## Conventions

- Package root: `tachiyomi.presentation.core.*`
- All components are Compose-only (no View system)
- Used by `app/` module's presentation layer
- No fork markers (shared infrastructure, not fork-specific)
