package lu.kaminski.dmarc.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Component
public class DateUtil {

    public static final DateTimeFormatter FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy 'at' HH:mm", Locale.ROOT);

    public String formatUptime() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Duration d = Duration.ofMillis(uptimeMs);
        long days = d.toDays();
        long hours = d.toHoursPart();
        long minutes = d.toMinutesPart();
        if (days > 0) return days + "j " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
