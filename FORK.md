# BonkMC Spyglass fork

This fork keeps Spyglass compatible with Folia and makes every backend server in a network read the same MariaDB/MySQL history.

## Network setup

Every Spyglass instance must use:

- `database.backend = "mariadb"` or `"mysql"`
- The same MariaDB/MySQL host, port, database, and credentials
- `network.enabled = true`
- A unique, stable `server.name`
- Matching world names for dimensions that should be queried or rolled back together

Example:

```hocon
database {
  backend = "mariadb"
  mariadb {
    host = "database.internal"
    port = 3306
    database = "spyglass"
    user = "spyglass"
    password = "change-me"
    ssl = true
  }
}

server {
  name = "survival-1"
}

network {
  enabled = true
  poll-interval-millis = 100
  sync-timeout-millis = 3000
  instance-timeout-millis = 10000
}
```

MariaDB/MySQL is both the shared record store and the coordination transport. Before a read, active instances flush their recorder queues and acknowledge the request through the shared database. Redis is not required.

Network coordination is implemented entirely in the Paper/Folia plugin. This fork does not build or ship a Velocity plugin, does not use proxy plugin messages, and does not require the backend servers to sit behind a proxy.

The network registry rejects duplicate server names and collisions in Spyglass's 8-bit event-ID server slot. Rename one of the conflicting servers if startup reports a slot conflict.

World UUIDs commonly differ between servers. Spatial and world predicates therefore match either UUID or world name. Rollback effects and search-result teleports resolve UUID first, then the same-named world on the current server. No proxy transfer is attempted.

## Folia behavior

The plugin declares `folia-supported: true`. Player, entity, block, container, rollback, relight, and GUI continuations run through entity or region schedulers. Global coordination uses the global-region scheduler, and database work uses asynchronous schedulers. The Paper-only off-thread NMS rollback writer is disabled on Folia.

## Updating from upstream

The repository uses `origin` for the BonkMC fork and `upstream` for the original Spyglass repository:

```bash
git fetch upstream
git switch main
git merge upstream/main
./gradlew test
./gradlew build
```

Fork-specific code is concentrated in:

- `spyglass/src/main/java/net/medievalrp/spyglass/plugin/network/`
- `spyglass/src/main/java/net/medievalrp/spyglass/plugin/util/WorldReference.java`
- The scheduling seam in `ServiceSupport`
- Name-aware SQL predicates and rollback world decoding

The upstream release workflows are intentionally deleted in this fork. Do not restore `.github/workflows/publish-central.yml` or `.github/workflows/release.yml` when resolving an upstream merge.

The upstream `spyglass-velocity` module is intentionally absent. Do not restore it or its Gradle, documentation, publishing, licensing, or regression references during an upstream merge.

If upstream changes scheduling, rollback application, storage predicates, or plugin bootstrap, resolve those areas by preserving the fork's scheduler ownership, read barrier, and UUID-or-world-name behavior. Keep `git rerere` enabled to reuse recurring conflict resolutions:

```bash
git config rerere.enabled true
```

Run the full test suite after every upstream merge. The MariaDB integration tests require a reachable Docker daemon; a build that reports those tests as skipped is not complete network integration verification.
