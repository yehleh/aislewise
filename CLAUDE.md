# CLAUDE.md

AisleWise is a single-module Android app (`app/`): a shopping list grouped by store and aisle, with Gemini-powered scanning of handwritten lists. See README.md for features and setup.

## Commands

- Build: `./gradlew assembleDebug`
- Install on device: `./gradlew installDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- One test class: `./gradlew testDebugUnitTest --tests 'com.yehleh.aislewise.data.ListGrouperTest'`

All tests are JVM unit tests under `app/src/test` (Robolectric for Room/Android). There are no instrumented tests.

## Architecture

- Compose + Material 3 UI, one package per screen under `feature/`, each with a `*Screen.kt` and a Hilt `*ViewModel.kt`. Navigation lives in `nav/AppNavHost.kt` (string routes in `Routes`).
- Data: Room (`data/db`) → repositories (`data/repo`) → ViewModels. DI is Hilt, configured in `di/AppModule.kt`.
- Domain model:
  - `CatalogItem` is everything the app remembers about an item; `ListEntry` is an item currently on the list. Deleting a list entry does not delete the catalog item.
  - `CatalogItem.normalizedName` is unique and comes from `ItemNameNormalizer`. Always look items up by normalized name.
  - `ItemStore` links items to stores. An item with no stores counts as available at every store.
  - Sections have a global `defaultOrder`; `StoreSectionOrder` overrides it per store. `ListGrouper` applies this ordering and the store filter.
  - `Store.color` null means use the `StorePalette` default.
- Scanning: `ListImageParser` interface, implemented by `GeminiListImageParser` (OkHttp REST + structured JSON; request/response shaping in `GeminiProtocol`). It tries each model in `GEMINI_MODELS` in order and falls back on transient errors (`ScanException.isTransient`). `ScanMatcher` matches parsed items against the catalog before the review screen.

## Conventions

- Database changes: bump `version` in `AppDatabase`, add a `Migration` to `Migrations.ALL`, commit the new exported schema in `app/schemas/`, and extend `MigrationTest`. Don't use destructive migration.
- Default sections are seeded in `Seed.DEFAULT_SECTIONS` on first create only.
- Secrets (`GEMINI_API_KEY`) come from `local.properties` via `BuildConfig`. Never commit them.
- Pure logic (grouping, normalizing, matching, protocol parsing) is kept out of Android classes so it can be unit tested. Add tests alongside changes to it.
- KDoc is used sparingly to explain *why*; match the existing comment density.
