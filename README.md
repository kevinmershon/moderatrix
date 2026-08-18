# Moderatrix

Daily activity / mood / vitals tracker. Rust server on the LAN + Android client with offline-first local storage and auto-sync.

## Server

```
cd server
MODERATRIX_BIND=192.168.1.100:7878 MODERATRIX_DATA_DIR=/path/to/data cargo run --release
```

- `MODERATRIX_BIND` defaults to `192.168.1.100:7878`.
- `MODERATRIX_DATA_DIR` defaults to `./data`.
- Data is stored as `config.json` (categories/activities, editable from the app) plus
  `activity_log.csv` and `vitals_log.csv` (append-only, one row per entry, deduped by client-generated id).

### API

- `GET /health`
- `GET /config`, `PUT /config`
- `POST /sync` — body `{ activities: [...], vitals: [...] }`, returns accepted ids (idempotent by id)
- `GET /day?date=YYYY-MM-DD`
- `GET /last-recorded`

## Android app

Kotlin + Jetpack Compose. Local Room database is the source of truth — every log/check-in write lands
in Room immediately and is usable offline. A WorkManager periodic job (every 15 min, network-constrained)
plus a one-off trigger after each write pushes unsynced rows to the server whenever it's reachable.

- Default server URL is baked in as `http://192.168.1.100:7878/` (`BuildConfig.SERVER_BASE_URL`), editable
  in the Config tab and persisted via DataStore.
- Reminders: fixed daily alarms at 8am/1pm/5pm/8pm, plus an hourly WorkManager check that notifies if
  nothing has been recorded in the last 3 hours.
- Config tab lets you add/rename/remove categories and activities (including the "Contradictory Factors"
  category), and edit the server URL.

Build: open in Android Studio, or from CLI:

```
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
