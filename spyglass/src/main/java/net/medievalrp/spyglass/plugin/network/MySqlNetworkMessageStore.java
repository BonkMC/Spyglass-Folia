package net.medievalrp.spyglass.plugin.network;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.medievalrp.spyglass.plugin.storage.MariaDbRecordStore;

final class MySqlNetworkMessageStore {

    private final MariaDbRecordStore recordStore;

    MySqlNetworkMessageStore(MariaDbRecordStore recordStore) {
        this.recordStore = recordStore;
    }

    void initialize() {
        recordStore.withWriteConnection(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS spyglass_network_flush_requests ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                        + "request_id CHAR(36) NOT NULL UNIQUE, "
                        + "sender_id CHAR(36) NOT NULL, "
                        + "created_at BIGINT NOT NULL, "
                        + "KEY idx_spyglass_network_request_created (created_at)) ENGINE=InnoDB");
                statement.execute("CREATE TABLE IF NOT EXISTS spyglass_network_flush_acks ("
                        + "request_id CHAR(36) NOT NULL, "
                        + "instance_id CHAR(36) NOT NULL, "
                        + "succeeded BOOLEAN NOT NULL, "
                        + "acked_at BIGINT NOT NULL, "
                        + "PRIMARY KEY (request_id, instance_id), "
                        + "KEY idx_spyglass_network_ack_time (acked_at)) ENGINE=InnoDB");
            }
            return null;
        });
    }

    long latestRequestId() {
        return recordStore.withReadConnection(connection -> {
            try (var statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "SELECT COALESCE(MAX(id), 0) FROM spyglass_network_flush_requests")) {
                rows.next();
                return rows.getLong(1);
            }
        });
    }

    void publish(UUID requestId, String senderId, long now) {
        recordStore.withWriteConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO spyglass_network_flush_requests "
                            + "(request_id, sender_id, created_at) VALUES (?, ?, ?)")) {
                statement.setString(1, requestId.toString());
                statement.setString(2, senderId);
                statement.setLong(3, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    List<FlushRequest> requestsAfter(long requestId) {
        return recordStore.withReadConnection(connection -> {
            List<FlushRequest> requests = new ArrayList<>();
            try (var statement = connection.prepareStatement(
                    "SELECT id, request_id, sender_id FROM spyglass_network_flush_requests "
                            + "WHERE id > ? ORDER BY id LIMIT 100")) {
                statement.setLong(1, requestId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        requests.add(new FlushRequest(
                                rows.getLong(1),
                                UUID.fromString(rows.getString(2)),
                                rows.getString(3)));
                    }
                }
            }
            return requests;
        });
    }

    void acknowledge(UUID requestId, String instanceId, boolean succeeded, long now) {
        recordStore.withWriteConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO spyglass_network_flush_acks "
                            + "(request_id, instance_id, succeeded, acked_at) VALUES (?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE succeeded = VALUES(succeeded), "
                            + "acked_at = VALUES(acked_at)")) {
                statement.setString(1, requestId.toString());
                statement.setString(2, instanceId);
                statement.setBoolean(3, succeeded);
                statement.setLong(4, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    Map<String, Boolean> acknowledgements(UUID requestId) {
        return recordStore.withReadConnection(connection -> {
            Map<String, Boolean> acknowledgements = new HashMap<>();
            try (var statement = connection.prepareStatement(
                    "SELECT instance_id, succeeded FROM spyglass_network_flush_acks "
                            + "WHERE request_id = ?")) {
                statement.setString(1, requestId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        acknowledgements.put(rows.getString(1), rows.getBoolean(2));
                    }
                }
            }
            return acknowledgements;
        });
    }

    void prune(long olderThan) {
        recordStore.withWriteConnection(connection -> {
            try (var acknowledgements = connection.prepareStatement(
                         "DELETE FROM spyglass_network_flush_acks WHERE acked_at < ?");
                 var requests = connection.prepareStatement(
                         "DELETE FROM spyglass_network_flush_requests WHERE created_at < ?")) {
                acknowledgements.setLong(1, olderThan);
                acknowledgements.executeUpdate();
                requests.setLong(1, olderThan);
                requests.executeUpdate();
            }
            return null;
        });
    }
}
