use anyhow::{Context, Result};
use chrono::NaiveDate;
use parking_lot::RwLock;
use std::collections::HashSet;
use std::fs::{self, File, OpenOptions};
use std::path::{Path, PathBuf};

use crate::model::{Activity, ActivityEntry, Category, Config, Period, VitalsEntry};

const ACTIVITY_LOG_CSV: &str = "activity_log.csv";
const VITALS_LOG_CSV: &str = "vitals_log.csv";
const CONFIG_JSON: &str = "config.json";

pub struct Store {
    data_dir: PathBuf,
    config: RwLock<Config>,
    activity_ids_seen: RwLock<HashSet<String>>,
    vitals_ids_seen: RwLock<HashSet<String>>,
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
        .map(|(id, name)| Category {
            id: id.to_string(),
            name: name.to_string(),
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

    let activities = acts
        .into_iter()
        .map(|(cat, id, name, freq, morning, noon, night)| Activity {
            id: id.to_string(),
            category_id: cat.to_string(),
            name: name.to_string(),
            target_freq_per_week: freq,
            archived: false,
            available_morning: morning,
            available_noon: noon,
            available_night: night,
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
        let vitals_ids_seen = load_vitals_ids(&data_dir)?;

        Ok(Store {
            data_dir,
            config: RwLock::new(config),
            activity_ids_seen: RwLock::new(activity_ids_seen),
            vitals_ids_seen: RwLock::new(vitals_ids_seen),
        })
    }

    pub fn get_config(&self) -> Config {
        self.config.read().clone()
    }

    pub fn set_config(&self, cfg: Config) -> Result<()> {
        let path = self.data_dir.join(CONFIG_JSON);
        fs::write(&path, serde_json::to_vec_pretty(&cfg)?)?;
        *self.config.write() = cfg;
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

        Ok(accepted)
    }

    pub fn append_vitals(&self, entries: Vec<VitalsEntry>) -> Result<Vec<String>> {
        let path = self.vitals_log_path();
        let is_new_file = !path.exists();
        let mut seen = self.vitals_ids_seen.write();

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
            wtr.serialize(VitalsRow {
                id: e.id.clone(),
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
            })?;
        }
        wtr.flush()?;

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

#[derive(serde::Serialize, serde::Deserialize)]
struct VitalsRow {
    id: String,
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
    Some(VitalsEntry {
        id: r.id,
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

fn load_vitals_ids(data_dir: &Path) -> Result<HashSet<String>> {
    let rows = read_vitals_rows(&data_dir.join(VITALS_LOG_CSV))?;
    Ok(rows.into_iter().map(|r| r.id).collect())
}
