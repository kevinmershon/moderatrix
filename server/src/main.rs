mod model;
mod store;

use axum::{
    extract::{Query, State},
    http::StatusCode,
    routing::{get, post},
    Json, Router,
};
use chrono::NaiveDate;
use model::{Config, LastRecordedResponse, SyncRequest, SyncResponse};
use std::net::SocketAddr;
use std::sync::Arc;
use store::Store;
use tower_http::cors::CorsLayer;
use tower_http::trace::TraceLayer;

type SharedStore = Arc<Store>;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt::init();

    let data_dir = std::env::var("MODERATRIX_DATA_DIR").unwrap_or_else(|_| "./data".to_string());
    let store = Arc::new(Store::open(&data_dir)?);

    let bind_addr: SocketAddr = std::env::var("MODERATRIX_BIND")
        .unwrap_or_else(|_| "192.168.1.100:7878".to_string())
        .parse()
        .expect("MODERATRIX_BIND must be host:port");

    let app = Router::new()
        .route("/health", get(health))
        .route("/config", get(get_config).put(put_config))
        .route("/sync", post(sync))
        .route("/day", get(get_day))
        .route("/last-recorded", get(last_recorded))
        .layer(CorsLayer::permissive())
        .layer(TraceLayer::new_for_http())
        .with_state(store);

    tracing::info!("moderatrix-server listening on {}", bind_addr);
    let listener = tokio::net::TcpListener::bind(bind_addr).await?;
    axum::serve(listener, app).await?;
    Ok(())
}

async fn health() -> &'static str {
    "ok"
}

async fn get_config(State(store): State<SharedStore>) -> Json<Config> {
    Json(store.get_config())
}

async fn put_config(
    State(store): State<SharedStore>,
    Json(cfg): Json<Config>,
) -> Result<StatusCode, (StatusCode, String)> {
    store
        .set_config(cfg)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    Ok(StatusCode::NO_CONTENT)
}

async fn sync(
    State(store): State<SharedStore>,
    Json(req): Json<SyncRequest>,
) -> Result<Json<SyncResponse>, (StatusCode, String)> {
    let accepted_activity_ids = store
        .append_activities(req.activities)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    let accepted_vitals_ids = store
        .append_vitals(req.vitals)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    Ok(Json(SyncResponse {
        accepted_activity_ids,
        accepted_vitals_ids,
        server_time_epoch_ms: chrono::Utc::now().timestamp_millis(),
    }))
}

#[derive(serde::Deserialize)]
struct DayQuery {
    date: NaiveDate,
}

async fn get_day(
    State(store): State<SharedStore>,
    Query(q): Query<DayQuery>,
) -> Result<Json<model::DayLog>, (StatusCode, String)> {
    let (activities, vitals) = store
        .read_day(q.date)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    Ok(Json(model::DayLog {
        date: q.date,
        activities,
        vitals,
    }))
}

async fn last_recorded(
    State(store): State<SharedStore>,
) -> Result<Json<LastRecordedResponse>, (StatusCode, String)> {
    let last = store
        .last_recorded_epoch_ms()
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;
    Ok(Json(LastRecordedResponse {
        last_recorded_epoch_ms: last,
    }))
}
