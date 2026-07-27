package net.medievalrp.spyglass.plugin.network;

import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import net.medievalrp.spyglass.plugin.storage.MariaDbRecordStore;

final class MySqlNetworkInstanceStore {

    private final MariaDbRecordStore recordStore;

    MySqlNetworkInstanceStore(MariaDbRecordStore recordStore) {
        this.recordStore = recordStore;
    }

    void initialize() {
        recordStore.withWriteConnection(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS spyglass_network_instances_v2 ("
                        + "instance_id CHAR(36) NOT NULL PRIMARY KEY, "
                        + "server_name VARCHAR(128) NOT NULL UNIQUE, "
                        + "event_instance SMALLINT UNSIGNED NOT NULL UNIQUE, "
                        + "heartbeat_at BIGINT NOT NULL, "
                        + "KEY idx_spyglass_network_heartbeat (heartbeat_at)) ENGINE=InnoDB");
            }
            return null;
        });
    }

    void register(
            String instanceId,
            String serverName,
            int eventInstance,
            long activeSince,
            long now) {
        recordStore.withWriteConnection(connection -> {
            try (var delete = connection.prepareStatement(
                    "DELETE FROM spyglass_network_instances_v2 WHERE heartbeat_at < ?")) {
                delete.setLong(1, activeSince);
                delete.executeUpdate();
            }
            try (var conflicts = connection.prepareStatement(
                    "SELECT server_name FROM spyglass_network_instances_v2 "
                            + "WHERE server_name = ? OR event_instance = ?")) {
                conflicts.setString(1, serverName);
                conflicts.setInt(2, eventInstance);
                try (ResultSet rows = conflicts.executeQuery()) {
                    if (rows.next()) {
                        throw new IllegalStateException(
                                "Network server name or event-id slot conflicts with active server '"
                                        + rows.getString(1) + "'");
                    }
                }
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO spyglass_network_instances_v2 "
                            + "(instance_id, server_name, event_instance, heartbeat_at) "
                            + "VALUES (?, ?, ?, ?)")) {
                insert.setString(1, instanceId);
                insert.setString(2, serverName);
                insert.setInt(3, eventInstance);
                insert.setLong(4, now);
                insert.executeUpdate();
            }
            return null;
        });
    }

    void heartbeat(String instanceId, String serverName, long now) {
        recordStore.withWriteConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "UPDATE spyglass_network_instances_v2 "
                            + "SET heartbeat_at = ? WHERE instance_id = ? AND server_name = ?")) {
                statement.setLong(1, now);
                statement.setString(2, instanceId);
                statement.setString(3, serverName);
                statement.executeUpdate();
            }
            return null;
        });
    }

    Set<String> activeInstances(String localInstanceId, long activeSince) {
        return recordStore.withReadConnection(connection -> {
            Set<String> instanceIds = new HashSet<>();
            try (var statement = connection.prepareStatement(
                    "SELECT instance_id FROM spyglass_network_instances_v2 "
                            + "WHERE instance_id <> ? AND heartbeat_at >= ?")) {
                statement.setString(1, localInstanceId);
                statement.setLong(2, activeSince);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        instanceIds.add(rows.getString(1));
                    }
                }
            }
            return instanceIds;
        });
    }

    void prune(long olderThan) {
        recordStore.withWriteConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "DELETE FROM spyglass_network_instances_v2 WHERE heartbeat_at < ?")) {
                statement.setLong(1, olderThan);
                statement.executeUpdate();
            }
            return null;
        });
    }

    void remove(String instanceId) {
        recordStore.withWriteConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "DELETE FROM spyglass_network_instances_v2 WHERE instance_id = ?")) {
                statement.setString(1, instanceId);
                statement.executeUpdate();
            }
            return null;
        });
    }
}
