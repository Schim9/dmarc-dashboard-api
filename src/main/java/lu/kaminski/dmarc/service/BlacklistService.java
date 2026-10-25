package lu.kaminski.dmarc.service;

import lombok.extern.slf4j.Slf4j;
import lu.kaminski.dmarc.dto.BlacklistCheckDto;
import lu.kaminski.dmarc.dto.BlacklistStatusDto;
import lu.kaminski.dmarc.dto.BlacklistTargetResultDto;
import lu.kaminski.dmarc.entity.BlacklistResult;
import lu.kaminski.dmarc.entity.BlacklistTargetType;
import lu.kaminski.dmarc.repository.BlacklistResultRepository;
import lu.kaminski.dmarc.repository.DmarcRecordRepository;
import lu.kaminski.dmarc.repository.KnownDomainRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BlacklistService {

    private record DnsblConfig(String host, String label) {}

    private static final List<DnsblConfig> IP_DNSBLS = List.of(
            new DnsblConfig("zen.spamhaus.org",    "Spamhaus ZEN"),
            new DnsblConfig("bl.spamcop.net",      "SpamCop"),
            new DnsblConfig("b.barracudacentral.org", "Barracuda"),
            new DnsblConfig("dnsbl.sorbs.net",     "SORBS")
    );

    private static final List<DnsblConfig> DOMAIN_DNSBLS = List.of(
            new DnsblConfig("dbl.spamhaus.org", "Spamhaus DBL"),
            new DnsblConfig("multi.surbl.org",  "SURBL")
    );

    private final BlacklistResultRepository repository;
    private final DmarcRecordRepository recordRepository;
    private final KnownDomainRepository domainRepository;
    private final NotifService notifService;

    public BlacklistService(BlacklistResultRepository repository,
                            DmarcRecordRepository recordRepository,
                            KnownDomainRepository domainRepository,
                            NotifService notifService) {
        this.repository = repository;
        this.recordRepository = recordRepository;
        this.domainRepository = domainRepository;
        this.notifService = notifService;
    }

    // -------------------------------------------------------------------------
    // Vérification complète (appelée par le scheduler ou manuellement)
    // -------------------------------------------------------------------------

    @Async
    @Transactional
    public void checkAll() {
        log.info("=== Début vérification blacklists ===");

        List<String> ips = recordRepository.findAllDistinctSourceIps().stream()
                .filter(ip -> !isPrivateOrInvalid(ip))
                .distinct()
                .collect(Collectors.toList());

        List<String> domains = domainRepository.findAllDomainNames();

        log.info("Vérification de {} IP(s) et {} domaine(s)", ips.size(), domains.size());

        repository.deleteAll();

        List<BlacklistResult> results = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        for (String ip : ips) {
            String reversed = reverseIp(ip);
            for (DnsblConfig dnsbl : IP_DNSBLS) {
                boolean listed = isListed(reversed + "." + dnsbl.host());
                if (listed) log.warn("IP {} listée sur {}", ip, dnsbl.label());
                results.add(build(ip, BlacklistTargetType.IP, dnsbl, listed, now));
            }
        }

        for (String domain : domains) {
            for (DnsblConfig dnsbl : DOMAIN_DNSBLS) {
                boolean listed = isListed(domain + "." + dnsbl.host());
                if (listed) log.warn("Domaine {} listé sur {}", domain, dnsbl.label());
                results.add(build(domain, BlacklistTargetType.DOMAIN, dnsbl, listed, now));
            }
        }

        repository.saveAll(results);

        long listedCount = results.stream().filter(BlacklistResult::isListed).count();
        log.info("=== Vérification terminée : {} résultats, {} alerte(s) ===",
                results.size(), listedCount);

        if (listedCount > 0) {
            notifyIssues(results.stream().filter(BlacklistResult::isListed).collect(Collectors.toList()));
        }
    }

    // -------------------------------------------------------------------------
    // Lecture
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public BlacklistStatusDto getStatusForDomain(String domain) {
        List<String> ips = recordRepository.findDistinctSourceIpsByDomain(domain);
        List<String> targets = new ArrayList<>(ips);
        targets.add(domain);

        List<BlacklistResult> allResults = repository.findByTargetIn(targets);
        if (allResults.isEmpty()) {
            return new BlacklistStatusDto(domain, false, null, List.of());
        }

        LocalDateTime checkedAt = allResults.stream()
                .map(BlacklistResult::getCheckedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        Map<String, List<BlacklistResult>> byTarget = allResults.stream()
                .collect(Collectors.groupingBy(BlacklistResult::getTarget));

        List<BlacklistTargetResultDto> targetDtos = byTarget.entrySet().stream()
                .map(e -> {
                    List<BlacklistResult> rs = e.getValue();
                    boolean clean = rs.stream().noneMatch(BlacklistResult::isListed);
                    LocalDateTime tCheckedAt = rs.stream()
                            .map(BlacklistResult::getCheckedAt)
                            .max(LocalDateTime::compareTo).orElse(null);
                    List<BlacklistCheckDto> checks = rs.stream()
                            .map(r -> new BlacklistCheckDto(r.getDnsbl(), r.getDnsblLabel(), r.isListed()))
                            .collect(Collectors.toList());
                    return new BlacklistTargetResultDto(
                            e.getKey(), rs.get(0).getTargetType().name(), clean, tCheckedAt, checks);
                })
                .sorted(Comparator.comparing(BlacklistTargetResultDto::targetType)
                        .thenComparing(BlacklistTargetResultDto::target))
                .collect(Collectors.toList());

        boolean hasIssues = targetDtos.stream().anyMatch(t -> !t.clean());
        return new BlacklistStatusDto(domain, hasIssues, checkedAt, targetDtos);
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> getSummaryByDomain() {
        Set<String> listedTargets = repository.findAllListed().stream()
                .map(BlacklistResult::getTarget)
                .collect(Collectors.toSet());

        List<String> domains = domainRepository.findAllDomainNames();
        Map<String, Boolean> summary = new LinkedHashMap<>();

        for (String domain : domains) {
            if (listedTargets.contains(domain)) {
                summary.put(domain, true);
                continue;
            }
            List<String> ips = recordRepository.findDistinctSourceIpsByDomain(domain);
            boolean hasIssue = ips.stream().anyMatch(listedTargets::contains);
            summary.put(domain, hasIssue);
        }
        return summary;
    }

    // -------------------------------------------------------------------------
    // Helpers DNS
    // -------------------------------------------------------------------------

    private boolean isListed(String lookup) {
        try {
            InetAddress.getByName(lookup);
            return true;
        } catch (UnknownHostException e) {
            return false;
        } catch (Exception e) {
            log.debug("Erreur DNS pour {} : {}", lookup, e.getMessage());
            return false;
        }
    }

    private boolean isPrivateOrInvalid(String ip) {
        if (ip == null || ip.isBlank() || ip.contains(":")) return true;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return true;
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            return a == 10
                    || a == 127
                    || a == 0
                    || a >= 240
                    || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && b == 168);
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private String reverseIp(String ip) {
        String[] parts = ip.split("\\.");
        return parts[3] + "." + parts[2] + "." + parts[1] + "." + parts[0];
    }

    private BlacklistResult build(String target, BlacklistTargetType type,
                                   DnsblConfig dnsbl, boolean listed, LocalDateTime now) {
        BlacklistResult r = new BlacklistResult();
        r.setTarget(target);
        r.setTargetType(type);
        r.setDnsbl(dnsbl.host());
        r.setDnsblLabel(dnsbl.label());
        r.setListed(listed);
        r.setCheckedAt(now);
        return r;
    }

    private void notifyIssues(List<BlacklistResult> listedResults) {
        String summary = listedResults.stream()
                .map(r -> r.getTarget() + " → " + r.getDnsblLabel())
                .collect(Collectors.joining("\n"));
        notifService.sendPushBulletNotif(
                "⛔ Blacklist détectée :\n" + summary,
                "DMARC ⛔ Blacklist"
        );
    }
}

