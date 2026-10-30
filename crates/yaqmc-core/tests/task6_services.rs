use std::{
    collections::HashMap,
    path::Path,
    sync::{Arc, Mutex},
};

use rusqlite::Connection;
use serde_json::json;
use tempfile::TempDir;
use yaqmc_core::{
    credentials::{CredentialError, CredentialStore, SpawnBlockingCredentialStore},
    storage::StorageService,
};

fn table_names(database: &Path) -> Vec<String> {
    let connection = Connection::open(database).expect("database opens");
    let mut statement = connection
        .prepare("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")
        .expect("table query prepares");
    statement
        .query_map([], |row| row.get(0))
        .expect("table query runs")
        .collect::<Result<Vec<_>, _>>()
        .expect("table names load")
}

#[test]
fn storage_uses_injected_roots_and_preserves_the_v6_schema() {
    let root = TempDir::new().expect("temporary root");
    let data_root = root.path().join("injected-data");
    let cache_root = root.path().join("injected-cache");
    let storage =
        StorageService::open(data_root.clone(), cache_root.clone()).expect("storage opens");
    let database = data_root.join("library.sqlite3");

    assert!(database.is_file());
    assert!(cache_root.is_dir());
    storage
        .set_setting("task6-setting", "persists")
        .expect("setting saves");
    storage
        .save_queue(&json!({ "queue": ["track-1"] }))
        .expect("queue saves");
    drop(storage);

    let reopened = StorageService::open(data_root.clone(), cache_root).expect("storage reopens");
    assert_eq!(
        reopened
            .get_setting("task6-setting")
            .expect("setting loads"),
        Some("persists".to_owned())
    );
    assert_eq!(
        reopened
            .load_queue::<serde_json::Value>()
            .expect("queue loads"),
        Some(json!({ "queue": ["track-1"] }))
    );
    drop(reopened);

    let connection = Connection::open(&database).expect("database opens");
    let version: u32 = connection
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .expect("schema version");
    let journal_mode: String = connection
        .pragma_query_value(None, "journal_mode", |row| row.get(0))
        .expect("journal mode");
    assert_eq!(version, 6);
    assert_eq!(journal_mode.to_ascii_lowercase(), "wal");
    drop(connection);
    assert_eq!(
        table_names(&database),
        vec![
            "app_settings",
            "cache_files",
            "listening_session_artists",
            "listening_sessions",
            "playback_history",
            "provider_cache",
            "queue_state",
            "recent_searches",
            "sqlite_sequence",
        ]
    );
}

#[derive(Default)]
struct InMemoryStore(Mutex<HashMap<String, String>>);

impl CredentialStore for InMemoryStore {
    fn load(&self, account: &str) -> Result<Option<String>, CredentialError> {
        Ok(self.0.lock().expect("store lock").get(account).cloned())
    }

    fn save(&self, account: &str, secret: &str) -> Result<(), CredentialError> {
        self.0
            .lock()
            .expect("store lock")
            .insert(account.to_owned(), secret.to_owned());
        Ok(())
    }

    fn delete(&self, account: &str) -> Result<(), CredentialError> {
        self.0.lock().expect("store lock").remove(account);
        Ok(())
    }
}

struct PanickingStore;

impl CredentialStore for PanickingStore {
    fn load(&self, _account: &str) -> Result<Option<String>, CredentialError> {
        panic!("credential test worker panic")
    }

    fn save(&self, _account: &str, _secret: &str) -> Result<(), CredentialError> {
        Ok(())
    }

    fn delete(&self, _account: &str) -> Result<(), CredentialError> {
        Ok(())
    }
}

#[tokio::test]
async fn credentials_round_trip_and_keep_blocking_failures_sanitized() {
    let backend: Arc<dyn CredentialStore> = Arc::new(InMemoryStore::default());
    let store = SpawnBlockingCredentialStore::new(backend);
    store
        .save("qqmusic-session", "session")
        .await
        .expect("credential saves");
    assert_eq!(
        store
            .load("qqmusic-session")
            .await
            .expect("credential loads"),
        Some("session".to_owned())
    );
    store
        .delete("qqmusic-session")
        .await
        .expect("credential deletes");
    assert_eq!(
        store
            .load("qqmusic-session")
            .await
            .expect("credential loads"),
        None
    );

    let panicking = SpawnBlockingCredentialStore::new(Arc::new(PanickingStore));
    let error = panicking
        .load("qqmusic-session")
        .await
        .expect_err("panic is a join failure");
    assert!(matches!(error, CredentialError::JoinFailed));
    assert_eq!(error.to_string(), "the secure credential worker failed");
}
