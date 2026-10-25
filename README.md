# AisleWise

An Android shopping list that sorts itself by store and aisle.

- **List**: shows what you need, grouped by section (Produce, Dairy, …) in the aisle order of the store you're in. Filter by store, or see everything. Items with no store can be bought anywhere, so they show up under every store.
- **Items**: a catalog of everything you've added before. It remembers each item's section and stores, so adding "eggs" again fills those in for you. Names are normalized, so "Eggs", "eggs" and "egg" match the same item.
- **Stores**: your stores, each with a badge color and its own section order.
- **Scan**: take or pick photos of a handwritten list. Gemini reads it, then you review the items before adding them. Items already in the catalog keep their remembered section and stores.

All data is stored on the device in a Room database. The only network call is the Gemini request when you scan a list.

## Requirements

- Android Studio (AGP 8.7, Kotlin 2.1), JDK 17
- A device or emulator on Android 8.0 (API 26) or newer

## Setup

Gemini is only needed for scanning; the rest of the app works without it. To turn it on, get an API key from [Google AI Studio](https://aistudio.google.com/apikey) and add it to `local.properties` (this file is gitignored):

```properties
GEMINI_API_KEY=your-key-here
# Optional: models to try in order. Later ones are used when earlier ones are overloaded.
# GEMINI_MODELS=gemini-flash-lite-latest,gemini-2.5-flash-lite
```

Rebuild after changing either value; they're compiled into `BuildConfig`.

## Build and test

```sh
./gradlew installDebug     # build and install on a connected device
./gradlew testDebugUnitTest  # unit tests (JVM, Robolectric)
```

## Project layout

```
app/src/main/java/com/yehleh/aislewise/
  data/db/        Room database, entities, DAOs, migrations, seed sections
  data/repo/      Repositories and ListGrouper (store filtering + aisle ordering)
  data/normalize/ ItemNameNormalizer (catalog matching key)
  di/             Hilt modules
  feature/        list, add, catalog, stores, scan: one package per screen
  nav/            Navigation graph and bottom bar
  ui/             Theme and shared components
app/schemas/      Exported Room schemas, used by migration tests
```

Built with Jetpack Compose, Material 3, Room, Hilt, OkHttp and kotlinx.serialization.
