use anyhow::{Context, Result};
use chrono::NaiveDate;
use parking_lot::RwLock;
use std::collections::{HashMap, HashSet};
use std::fs::{self, File, OpenOptions};
use std::path::{Path, PathBuf};

use crate::model::{Activity, ActivityEntry, Category, Config, Period, VitalsEntry};

const ACTIVITY_LOG_CSV: &str = "activity_log.csv";
const VITALS_LOG_CSV: &str = "vitals_log.csv";
const CONFIG_JSON: &str = "config.json";
const CONSOLIDATED_CSV: &str = "consolidated.csv";

type VitalsKey = (String, String); // (date, period)

pub struct Store {
    data_dir: PathBuf,
    config: RwLock<Config>,
    activity_ids_seen: RwLock<HashSet<String>>,
    vitals_by_key: RwLock<HashMap<VitalsKey, VitalsRow>>,
}

fn default_config() -> Config {
    let cats = [
        ("existential", "Existential"),
        ("nurturing", "Nurturing"),
        ("neuroplasticity", "Neuroplasticity"),
        ("creative", "Creative"),
        ("contradictory_factors", "Contradictory Factors"),
    ];
    let categories: Vec<Category> = cats
        .iter()
        .enumerate()
        .map(|(index, (id, name))| Category {
            id: id.to_string(),
            name: name.to_string(),
            sort_order: index as i32,
        })
        .collect();

    // (category, id, name, freq/week, available_morning, available_noon, available_night)
    let acts: Vec<(&str, &str, &str, u32, bool, bool, bool)> = vec![
        ("existential", "productive_work", "Productive Work", 5, true, true, true),
        ("existential", "cook_dinner", "Cook Dinner", 3, false, true, true),
        ("existential", "work_out", "Work out", 7, true, true, true),
        ("existential", "sunlight", "Sunlight / Outside time", 7, true, true, false),
        ("existential", "evening_walk", "Evening Walk", 3, false, false, true),
        ("nurturing", "brush_cat", "Brush Cat", 3, true, true, true),
        ("nurturing", "be_social", "Be Social / Go Out", 3, true, true, true),
        ("nurturing", "household_chores", "Household chores", 3, true, true, true),
        ("neuroplasticity", "wordle", "Wordle", 5, true, true, true),
        ("neuroplasticity", "duolingo", "Duolingo", 7, true, true, true),
        ("neuroplasticity", "read", "Read", 3, true, true, true),
        ("neuroplasticity", "piano", "Piano", 3, true, true, true),
        ("creative", "hobby_coding", "Hobby Coding", 3, true, true, true),
        ("creative", "music_composition", "Music Composition", 2, true, true, true),
        ("creative", "learning_blender", "Learning Blender", 3, true, true, true),
        ("contradictory_factors", "reprimanded_at_work", "Reprimanded at Work", 0, true, true, true),
        ("contradictory_factors", "relationship_conflict", "Relationship Conflict", 0, true, true, true),
        ("contradictory_factors", "poor_quality_sleep", "Poor quality sleep", 0, true, false, false),
        ("contradictory_factors", "refined_sugars", "Refined sugars", 0, true, true, true),
        ("contradictory_factors", "processed_foods", "Processed foods", 0, true, true, true),
        ("contradictory_factors", "alcohol_kombucha_soda", "Alcohol / Kombucha / Soda", 0, true, true, true),
        ("contradictory_factors", "excess_snacking", "Excess Snacking", 0, true, true, true),
        ("contradictory_factors", "high_leisure_screen_time", "High Leisure Screen Time", 0, true, true, true),
    ];

    let mut next_sort_order_by_category: std::collections::HashMap<&str, i32> =
        std::collections::HashMap::new();
    let activities = acts
        .into_iter()
        .map(|(cat, id, name, freq, morning, noon, night)| {
            let sort_order = *next_sort_order_by_category
                .entry(cat)
                .and_modify(|n| *n += 1)
                .or_insert(0);
            Activity {
                id: id.to_string(),
                category_id: cat.to_string(),
                name: name.to_string(),
                target_freq_per_week: freq,
                archived: false,
                available_morning: morning,
                available_noon: noon,
                available_night: night,
                sort_order,
            }
        })
        .collect();

    Config {
        categories,
        activities,
    }
}

impl Store {
    pub fn open(data_dir: impl AsRef<Path>) -> Result<Self> {
        let data_dir = data_dir.as_ref().to_path_buf();
        fs::create_dir_all(&data_dir)
            .with_context(|| format!("creating data dir {:?}", data_dir))?;

        let config_path = data_dir.join(CONFIG_JSON);
        let config = if config_path.exists() {
            let bytes = fs::read(&config_path)?;
            serde_json::from_slice(&bytes).context("parsing config.json")?
        } else {
            let cfg = default_config();
            fs::write(&config_path, serde_json::to_vec_pretty(&cfg)?)?;
            cfg
        };

        let activity_ids_seen = load_activity_ids(&data_dir)?;
        let vitals_by_key = load_vitals_by_key(&data_dir)?;

        let store = Store {
            data_dir,
            config: RwLock::new(config),
            activity_ids_seen: RwLock::new(activity_ids_seen),
            vitals_by_key: RwLock::new(vitals_by_key),
        };
        store.write_consolidated()?;
        Ok(store)
    }

    pub fn get_config(&self) -> Config {
        self.config.read().clone()
    }

    pub fn set_config(&self, cfg: Config) -> Result<()> {
        let path = self.data_dir.join(CONFIG_JSON);
        fs::write(&path, serde_json::to_vec_pretty(&cfg)?)?;
        *self.config.write() = cfg;
        self.write_consolidated()?;
        Ok(())
    }

    fn activity_log_path(&self) -> PathBuf {
        self.data_dir.join(ACTIVITY_LOG_CSV)
    }

    fn vitals_log_path(&self) -> PathBuf {
        self.data_dir.join(VITALS_LOG_CSV)
    }

    /// Returns the ids actually newly appended (dedupes against previously seen ids).
    pub fn append_activities(&self, entries: Vec<ActivityEntry>) -> Result<Vec<String>> {
        let path = self.activity_log_path();
        let is_new_file = !path.exists();
        let mut seen = self.activity_ids_seen.write();

        let mut to_write = Vec::new();
        let mut accepted = Vec::new();
        for e in entries {
            if seen.contains(&e.id) {
                continue;
            }
            seen.insert(e.id.clone());
            accepted.push(e.id.clone());
            to_write.push(e);
        }
        drop(seen);

        if to_write.is_empty() {
            return Ok(accepted);
        }

        let file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&path)?;
        let mut wtr = csv::WriterBuilder::new()
            .has_headers(is_new_file)
            .from_writer(file);

        for e in &to_write {
            wtr.serialize(ActivityRow {
                id: e.id.clone(),
                date: e.date.to_string(),
                period: e.period.as_str().to_string(),
                activity_id: e.activity_id.clone(),
                done: e.done,
                recorded_at_epoch_ms: e.recorded_at_epoch_ms,
            })?;
        }
        wtr.flush()?;

        self.write_consolidated()?;
        Ok(accepted)
    }

    /// Upserts by (date, period): only the latest snapshot per day/period is kept.
    /// Entries are accepted (and the whole file rewritten) whenever they are newer than
    /// whatever is currently stored for that key, or no row exists for it yet.
    pub fn append_vitals(&self, entries: Vec<VitalsEntry>) -> Result<Vec<String>> {
        let path = self.vitals_log_path();
        let mut by_key = self.vitals_by_key.write();

        let mut accepted = Vec::new();
        let mut changed = false;
        for e in entries {
            let key = (e.date.to_string(), e.period.as_str().to_string());
            let is_newer = by_key
                .get(&key)
                .map_or(true, |existing: &VitalsRow| {
                    e.recorded_at_epoch_ms >= existing.recorded_at_epoch_ms
                });
            if is_newer {
                by_key.insert(
                    key,
                    VitalsRow {
                        date: e.date.to_string(),
                        period: e.period.as_str().to_string(),
                        mood: e.mood,
                        alertness: e.alertness,
                        energy: e.energy,
                        pain: e.pain,
                        satiety: e.satiety,
                        hydration: e.hydration,
                        notes: e.notes.clone().unwrap_or_default(),
                        recorded_at_epoch_ms: e.recorded_at_epoch_ms,
                    },
                );
                changed = true;
            }
            accepted.push(e.id.clone());
        }

        if changed {
            let mut rows: Vec<&VitalsRow> = by_key.values().collect();
            rows.sort_by(|a, b| (&a.date, &a.period).cmp(&(&b.date, &b.period)));

            let file = File::create(&path)?;
            let mut wtr = csv::WriterBuilder::new().has_headers(true).from_writer(file);
            for row in rows {
                wtr.serialize(row)?;
            }
            wtr.flush()?;
        }

        self.write_consolidated()?;
        Ok(accepted)
    }

    pub fn read_day(&self, date: NaiveDate) -> Result<(Vec<ActivityEntry>, Vec<VitalsEntry>)> {
        let activities = read_activity_rows(&self.activity_log_path())?
            .into_iter()
            .filter(|r| r.date == date.to_string())
            .filter_map(row_to_activity)
            .collect();

        let vitals = read_vitals_rows(&self.vitals_log_path())?
            .into_iter()
            .filter(|r| r.date == date.to_string())
            .filter_map(row_to_vitals)
            .collect();

        Ok((activities, vitals))
    }

    /// Most recent recorded_at_epoch_ms across both logs, if any.
    pub fn last_recorded_epoch_ms(&self) -> Result<Option<i64>> {
        let mut max: Option<i64> = None;
        for r in read_activity_rows(&self.activity_log_path())? {
            max = Some(max.map_or(r.recorded_at_epoch_ms, |m| m.max(r.recorded_at_epoch_ms)));
        }
        for r in read_vitals_rows(&self.vitals_log_path())? {
            max = Some(max.map_or(r.recorded_at_epoch_ms, |m| m.max(r.recorded_at_epoch_ms)));
        }
        Ok(max)
    }

    /// Rewrites a normalized, spreadsheet-friendly view: one row per date, one column per
    /// non-archived activity (1 if done in any period that day, else 0), then averaged vitals
    /// across whatever periods were recorded that day, then notes from all periods concatenated
    /// with period labels. Morning/noon/night detail is intentionally collapsed away here since
    /// it isn't useful for day-over-day trend analysis in a spreadsheet.
    fn write_consolidated(&self) -> Result<()> {
        let config = self.config.read().clone();
        let mut activities: Vec<&Activity> = config.activities.iter().filter(|a| !a.archived).collect();
        activities.sort_by_key(|a| {
            let cat_order = config
                .categories
                .iter()
                .find(|c| c.id == a.category_id)
                .map(|c| c.sort_order)
                .unwrap_or(i32::MAX);
            (cat_order, a.sort_order)
        });

        let activity_rows = read_activity_rows(&self.activity_log_path())?;
        let vitals_rows = read_vitals_rows(&self.vitals_log_path())?;

        let mut dates: std::collections::BTreeSet<String> = std::collections::BTreeSet::new();
        for r in &activity_rows {
            dates.insert(r.date.clone());
        }
        for r in &vitals_rows {
            dates.insert(r.date.clone());
        }

        let path = self.data_dir.join(CONSOLIDATED_CSV);
        let file = File::create(&path)?;
        let mut wtr = csv::WriterBuilder::new().from_writer(file);

        let mut header: Vec<String> = vec!["date".to_string()];
        header.extend(activities.iter().map(|a| a.name.clone()));
        header.extend(
            ["mood", "alertness", "energy", "pain", "satiety", "hydration"]
                .iter()
                .map(|s| s.to_string()),
        );
        header.push("notes".to_string());
        wtr.write_record(&header)?;

        for date in &dates {
            let mut row: Vec<String> = vec![date.clone()];

            let done_today: HashSet<&str> = activity_rows
                .iter()
                .filter(|r| &r.date == date && r.done)
                .map(|r| r.activity_id.as_str())
                .collect();
            for activity in &activities {
                row.push(if done_today.contains(activity.id.as_str()) { "1" } else { "0" }.to_string());
            }

            let day_vitals: Vec<&VitalsRow> = vitals_rows.iter().filter(|r| &r.date == date).collect();
            for field in [
                VitalsField::Mood,
                VitalsField::Alertness,
                VitalsField::Energy,
                VitalsField::Pain,
                VitalsField::Satiety,
                VitalsField::Hydration,
            ] {
                let values: Vec<i32> = day_vitals.iter().filter_map(|r| field.get(r)).collect();
                row.push(if values.is_empty() {
                    String::new()
                } else {
                    let avg = values.iter().sum::<i32>() as f64 / values.len() as f64;
                    format!("{:.1}", avg)
                });
            }

            let notes = day_vitals
                .iter()
                .filter(|r| !r.notes.is_empty())
                .map(|r| format!("[{}] {}", r.period, r.notes))
                .collect::<Vec<_>>()
                .join("; ");
            row.push(notes);

            wtr.write_record(&row)?;
        }

        wtr.flush()?;
        Ok(())
    }
}

#[derive(Clone, Copy)]
enum VitalsField {
    Mood,
    Alertness,
    Energy,
    Pain,
    Satiety,
    Hydration,
}

impl VitalsField {
    fn get(&self, row: &VitalsRow) -> Option<i32> {
        match self {
            VitalsField::Mood => row.mood,
            VitalsField::Alertness => row.alertness,
            VitalsField::Energy => row.energy,
            VitalsField::Pain => row.pain,
            VitalsField::Satiety => row.satiety,
            VitalsField::Hydration => row.hydration,
        }
    }
}

#[derive(serde::Serialize, serde::Deserialize)]
struct ActivityRow {
    id: String,
    date: String,
    period: String,
    activity_id: String,
    done: bool,
    recorded_at_epoch_ms: i64,
}

#[derive(serde::Serialize, serde::Deserialize, Clone)]
struct VitalsRow {
    date: String,
    period: String,
    mood: Option<i32>,
    alertness: Option<i32>,
    energy: Option<i32>,
    pain: Option<i32>,
    satiety: Option<i32>,
    hydration: Option<i32>,
    notes: String,
    recorded_at_epoch_ms: i64,
}

fn read_activity_rows(path: &Path) -> Result<Vec<ActivityRow>> {
    if !path.exists() {
        return Ok(Vec::new());
    }
    let file = File::open(path)?;
    let mut rdr = csv::Reader::from_reader(file);
    let mut out = Vec::new();
    for rec in rdr.deserialize() {
        let row: ActivityRow = rec?;
        out.push(row);
    }
    Ok(out)
}

fn read_vitals_rows(path: &Path) -> Result<Vec<VitalsRow>> {
    if !path.exists() {
        return Ok(Vec::new());
    }
    let file = File::open(path)?;
    let mut rdr = csv::Reader::from_reader(file);
    let mut out = Vec::new();
    for rec in rdr.deserialize() {
        let row: VitalsRow = rec?;
        out.push(row);
    }
    Ok(out)
}

fn row_to_activity(r: ActivityRow) -> Option<ActivityEntry> {
    Some(ActivityEntry {
        id: r.id,
        date: r.date.parse().ok()?,
        period: Period::parse(&r.period)?,
        activity_id: r.activity_id,
        done: r.done,
        recorded_at_epoch_ms: r.recorded_at_epoch_ms,
    })
}

fn row_to_vitals(r: VitalsRow) -> Option<VitalsEntry> {
    let id = format!("{}-{}", r.date, r.period);
    Some(VitalsEntry {
        id,
        date: r.date.parse().ok()?,
        period: Period::parse(&r.period)?,
        mood: r.mood,
        alertness: r.alertness,
        energy: r.energy,
        pain: r.pain,
        satiety: r.satiety,
        hydration: r.hydration,
        notes: if r.notes.is_empty() { None } else { Some(r.notes) },
        recorded_at_epoch_ms: r.recorded_at_epoch_ms,
    })
}

fn load_activity_ids(data_dir: &Path) -> Result<HashSet<String>> {
    let rows = read_activity_rows(&data_dir.join(ACTIVITY_LOG_CSV))?;
    Ok(rows.into_iter().map(|r| r.id).collect())
}

fn load_vitals_by_key(data_dir: &Path) -> Result<HashMap<VitalsKey, VitalsRow>> {
    let rows = read_vitals_rows(&data_dir.join(VITALS_LOG_CSV))?;
    Ok(rows
        .into_iter()
        .map(|r| ((r.date.clone(), r.period.clone()), r))
        .collect())
}

