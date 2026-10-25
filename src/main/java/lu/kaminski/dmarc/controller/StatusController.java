package lu.kaminski.dmarc.controller;

import lombok.RequiredArgsConstructor;
import lu.kaminski.dmarc.util.DateUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StatusController {

    private final DateUtil dateUtil;

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appCode", "DMARC_DASHBOARD");
        body.put("status", "up");
        body.put("uptime", dateUtil.formatUptime());
        body.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).toString());
        return ResponseEntity.ok(body);
    }
}
