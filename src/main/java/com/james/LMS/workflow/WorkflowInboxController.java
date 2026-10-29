package com.james.LMS.workflow;

import com.james.LMS.config.SecurityUserDetails;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Single database-based inbox shared by Customer and Chef; no UI-side guesswork about peers. */
@RestController
@RequestMapping("/api/workflow")
@PreAuthorize("isAuthenticated()")
public class WorkflowInboxController {
  private final NamedParameterJdbcTemplate jdbc;
  public WorkflowInboxController(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

  @GetMapping("/threads")
  public List<Map<String,Object>> threads(@AuthenticationPrincipal SecurityUserDetails actor) {
    return jdbc.queryForList("""
      SELECT 'REQUEST' AS "kind",r.id AS "scopeId",
        CASE WHEN r.customer_id=:actor THEN m.chef_id ELSE r.customer_id END AS "peerId",
        r.request_code AS "title",
        CASE WHEN r.customer_id=:actor THEN cp.display_name ELSE u.username END AS "peerName",
        (SELECT MAX(x.created_at) FROM cooking_request_messages x
         WHERE x.request_id=r.id AND
          ((x.sender_id=:actor AND x.recipient_id=CASE WHEN r.customer_id=:actor THEN m.chef_id ELSE r.customer_id END)
           OR (x.recipient_id=:actor AND x.sender_id=CASE WHEN r.customer_id=:actor THEN m.chef_id ELSE r.customer_id END))) AS "lastMessageAt"
      FROM cooking_request_matches m JOIN public_cooking_requests r ON r.id=m.request_id
      JOIN users u ON u.id=r.customer_id JOIN chef_profiles cp ON cp.user_id=m.chef_id
      WHERE r.customer_id=:actor OR m.chef_id=:actor
      UNION ALL
      SELECT 'ORDER' AS "kind",o.id AS "scopeId",
        CASE WHEN o.customer_id=:actor THEN o.chef_id ELSE o.customer_id END AS "peerId",
        o.order_code AS "title",
        CASE WHEN o.customer_id=:actor THEN COALESCE(o.chef_name,'Đầu bếp') ELSE o.customer_name END AS "peerName",
        (SELECT MAX(x.created_at) FROM order_messages x WHERE x.order_id=o.id) AS "lastMessageAt"
      FROM orders o WHERE (o.customer_id=:actor OR o.chef_id=:actor)
        AND o.customer_id IS NOT NULL AND o.chef_id IS NOT NULL
      ORDER BY "lastMessageAt" DESC NULLS LAST, "scopeId" DESC
      LIMIT 150
      """, new MapSqlParameterSource("actor",actor.getId()));
  }
}
