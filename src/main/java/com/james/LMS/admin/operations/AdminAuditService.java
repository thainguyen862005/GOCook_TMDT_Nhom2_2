package com.james.LMS.admin.operations;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Append-only, intentionally excludes passwords, tokens and payment secrets. */
@Service
public class AdminAuditService {
    private final NamedParameterJdbcTemplate jdbc;

    public AdminAuditService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(Long actorId, String action, String entity, Long id, String note) {
        jdbc.update("""
                INSERT INTO admin_audit_logs(actor_id, action, entity_type, entity_id, details)
                VALUES (:actor, :action, :entity, :id, :note)
                """, new MapSqlParameterSource()
                .addValue("actor", actorId).addValue("action", action)
                .addValue("entity", entity).addValue("id", id)
                .addValue("note", note == null ? "" : note.substring(0, Math.min(note.length(), 1500))));
    }
}
