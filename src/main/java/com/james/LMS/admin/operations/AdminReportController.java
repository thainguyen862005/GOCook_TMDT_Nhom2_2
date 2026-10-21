package com.james.LMS.admin.operations;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A14: exported rows show their source, not conflated platform profit and GMV. */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
public class AdminReportController {
    private final NamedParameterJdbcTemplate jdbc;
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    public AdminReportController(NamedParameterJdbcTemplate jdbc) {this.jdbc=jdbc;}

    @GetMapping("/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue="payments") String type,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(VN) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (end.isBefore(start) || start.plusYears(1).isBefore(end)) {
            throw new IllegalArgumentException("Khoảng xuất dữ liệu tối đa một năm.");
        }
        String sql;
        String[] columns;
        if ("payments".equals(type)) {
            sql="""
                SELECT pt.id, o.order_code, pt.provider, pt.status, pt.amount,
                    pt.transaction_code, pt.paid_at
                FROM payment_transactions pt LEFT JOIN orders o ON o.id=pt.order_id
                WHERE pt.paid_at >= :from AND pt.paid_at < :to
                ORDER BY pt.paid_at DESC, pt.id DESC LIMIT 20000
                """;
            columns=new String[]{"id","order_code","provider","status","amount","transaction_code","paid_at"};
        } else if ("bookings".equals(type)) {
            sql="""
                SELECT id,order_code,customer_name,chef_name,status,total_amount,scheduled_at
                FROM orders WHERE scheduled_at >= :from AND scheduled_at < :to
                ORDER BY scheduled_at DESC, id DESC LIMIT 20000
                """;
            columns=new String[]{"id","order_code","customer_name","chef_name","status","total_amount","scheduled_at"};
        } else throw new IllegalArgumentException("Chỉ hỗ trợ bookings hoặc payments.");

        Object fromValue = "payments".equals(type) ? start.atStartOfDay(VN).toOffsetDateTime() : start.atStartOfDay();
        Object toValue = "payments".equals(type) ? end.plusDays(1).atStartOfDay(VN).toOffsetDateTime() : end.plusDays(1).atStartOfDay();
        MapSqlParameterSource params=new MapSqlParameterSource("from",fromValue).addValue("to",toValue);
        List<Map<String,Object>> rows=jdbc.queryForList(sql,params);
        StringBuilder csv=new StringBuilder("\uFEFF");
        csv.append(String.join(",",columns)).append("\r\n");
        for(Map<String,Object> row: rows) {
            for(int i=0;i<columns.length;i++) {
                if(i>0) csv.append(',');
                String text=String.valueOf(row.get(columns[i])==null ? "" : row.get(columns[i]));
                // Prevent spreadsheet formula injection while preserving RFC4180 escaping.
                if(!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0))>=0) text="'"+text;
                csv.append('"').append(text.replace("\"","\"\"")).append('"');
            }
            csv.append("\r\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=admin-"+type+".csv")
                .contentType(new MediaType("text","csv",StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
}
