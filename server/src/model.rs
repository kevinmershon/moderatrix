use chrono::{NaiveDate};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Period {
    Morning,
    Noon,
    Night,
}

impl Period {
    pub fn as_str(&self) -> &'static str {
        match self {
            Period::Morning => "morning",
            Period::Noon => "noon",
            Period::Night => "night",
        }
    }

    pub fn parse(s: &str) -> Option<Period> {
        match s {
            "morning" => Some(Period::Morning),
            "noon" => Some(Period::Noon),
            "night" => Some(Period::Night),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Category {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub sort_order: i32,
}

fn default_true() -> bool {
    true
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Activity {
    pub id: String,
    pub category_id: String,
    pub name: String,
    pub target_freq_per_week: u32,
    pub archived: bool,
    #[serde(default = "default_true")]
    pub available_morning: bool,
    #[serde(default = "default_true")]
    pub available_noon: bool,
    #[serde(default = "default_true")]
    pub available_night: bool,
    #[serde(default)]
    pub sort_order: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    pub categories: Vec<Category>,
    pub activities: Vec<Activity>,
}

/// One record: did `activity_id` happen on `date` during `period`.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActivityEntry {
    pub id: String, // client-generated UUID, used for idempotent upsert
    pub date: NaiveDate,
    pub period: Period,
    pub activity_id: String,
    pub done: bool,
    pub recorded_at_epoch_ms: i64,
}

/// Subjective state snapshot at a point in time.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VitalsEntry {
    pub id: String, // client-generated UUID
    pub date: NaiveDate,
    pub period: Period,
    pub mood: Option<i32>,       // e.g. 1-5
    pub alertness: Option<i32>,  // 1-5
    pub energy: Option<i32>,     // 1-5
    pub pain: Option<i32>,       // 0-5
    pub satiety: Option<i32>,    // 1-5, 1=starving 5=stuffed
    pub hydration: Option<i32>,  // 1-5
    pub notes: Option<String>,
    pub recorded_at_epoch_ms: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DayLog {
    pub date: NaiveDate,
    pub activities: Vec<ActivityEntry>,
    pub vitals: Vec<VitalsEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncRequest {
    #[serde(default)]
    pub activities: Vec<ActivityEntry>,
    #[serde(default)]
    pub vitals: Vec<VitalsEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncResponse {
    pub accepted_activity_ids: Vec<String>,
    pub accepted_vitals_ids: Vec<String>,
    pub server_time_epoch_ms: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LastRecordedResponse {
    pub last_recorded_epoch_ms: Option<i64>,
}
