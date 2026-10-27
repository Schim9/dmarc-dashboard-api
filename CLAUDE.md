# CLAUDE.md — dmarc-dashboard-api

Backend Spring Boot du projet DMARC Dashboard.

**Repo GitHub** : https://github.com/Schim9/dmarc-dashboard-api
**Déploiement** : voir skill `/info-pi-apps`
**Langue** : Communiquer en français avec l'utilisateur.

---

## Stack

- Java 21 / Spring Boot 3.3
- MySQL (production) / H2 (tests)
- Package racine : `lu.kaminski.dmarc`

## Commandes

```bash
# Développement
mvn spring-boot:run

# Tests (H2 en mémoire)
mvn test

# Build JAR pour déploiement
mvn clean package -DskipTests
# → target/dmarc-dashboard-api-0.0.1-SNAPSHOT.jar
```

## Variables d'environnement requises

Ces variables **n'ont pas de valeur par défaut** et doivent être définies :

| Variable | Description |
|---|---|
| `DB_USERNAME` | Utilisateur MySQL |
| `DB_PASSWORD` | Mot de passe MySQL |
| `ADMIN_PASSWORD` | Mot de passe admin interface |
| `JWT_SECRET` | Clé secrète de signature des tokens JWT |

Variables optionnelles (avec défauts) :

| Variable | Défaut | Description |
|---|---|---|
| `DB_HOST` | `localhost` | Hôte MySQL |
| `DB_PORT` | `3306` | Port MySQL |
| `DB_NAME` | `dmarc_dashboard` | Nom de la base |
| `DMARC_SOURCE_MODE` | `folder` | `folder` ou `mail` |
| `DMARC_FOLDER_PATH` | `./dmarc-reports` | Dossier dépôt XML (mode folder) |
| `ADMIN_USERNAME` | `admin` | Login admin |
| `PUSHBULLET_API_KEY` | *(vide)* | Notifications PushBullet |
| `CORS_ORIGINS` | `https://dmarc-dashboard-app.kaminski.lu,http://localhost:4200` | Origines CORS autorisées |
| `MAIL_HOST` | `imap.example.com` | Serveur IMAP (mode mail) |
| `MAIL_USERNAME` | `dmarc@example.com` | Login IMAP |
| `MAIL_PASSWORD` | *(vide)* | Mot de passe IMAP |
| `SCHEDULER_ENABLED` | `true` | Active/désactive les schedulers |
| `SCHEDULER_CHECK_MAILBOX` | `0 0 */6 * * *` | Cron collecte DMARC (toutes les 6h) |
| `SCHEDULER_WEEKLY_REPORT` | `0 0 17 * * FRI` | Cron résumé hebdomadaire (vendredi 17h) |
| `SCHEDULER_BLACKLIST` | `0 0 2 * * *` | Cron vérification blacklist (2h du matin) |
| `SCHEDULER_DNS_CHECK` | `0 0 3 * * *` | Cron vérification DNS (3h du matin) |
| `JWT_EXPIRATION` | `86400000` | Durée de validité du token JWT (ms) |

## Architecture

```
controller/     REST endpoints (Auth, Domain, Report, Intervention, Blacklist, DnsCheck, Fetch, Status)
service/        Logique métier
  ├── MailFetchService / FileFetchService   Collecte rapports IMAP ou dossier local
  ├── DmarcParserService                    Parsing XML DMARC via JAXB
  ├── ReportService                         Import + déduplication par reportId
  ├── IpResolverService                     Reverse DNS des IPs sources
  ├── InterventionService                   Gestion des interventions + journal
  ├── BlacklistService                      Vérification blacklist DNS
  └── DnsCheckService                       Vérification SPF/DKIM/DMARC en DNS
scheduler/      DmarcScheduler (6h + vendredi), BlacklistScheduler, DnsCheckScheduler
entity/         JPA : DmarcReport, DmarcRecord, KnownDomain, Intervention,
                InterventionJournal, BlacklistResult, DnsCheck
repository/     Spring Data JPA
dto/            Java records (requêtes et réponses)
xml/            Classes JAXB (schéma DMARC RUA)
config/         SecurityConfig, CorsConfig, PushBulletProperties
```

## Authentification

JWT stateless (`SessionCreationPolicy.STATELESS`). Un seul utilisateur admin en mémoire (`InMemoryUserDetailsManager`). Le token JWT est signé avec `JWT_SECRET` et retourné au login ; le frontend le transmet via header `Authorization: Bearer <token>`.
Le CSRF est désactivé (stateless par nature).

## Points de vigilance connus (à corriger)

- **`InterventionStatus.valueOf()`** non protégé → message d'erreur 400 expose le nom complet de l'enum Java
- **`@Autowired` en injection par champ** dans `MailFetchService` — incohérence par rapport à l'injection constructeur utilisée partout ailleurs
- **Sérialisation JSON brute (`TEXT`)** dans l'entité `DnsCheck` pour `dkimChecksJson` et `issuesJson` — pas de mapping objet, désérialisé manuellement
- **N+1 dans `ReportService.getAllDomainStats()`** — lookup SPF réseau par domaine déclenché pour chaque domaine lors de l'agrégation
- **`LocalDateTime.now()` sans timezone** dans `InterventionService` (ligne ~127) — comportement dépendant du fuseau du serveur
- **Tests insuffisants** — un seul `contextLoads()`, aucun test métier

## Historique des décisions

- **2026-10** : Suppression du mode démo (`dmarc.demo.mode`) — fonctionnalité retirée définitivement
- **2026-10** : Revue sécurité pré-GitHub — credentials retirés de `application.yml`, `.gitignore` créé
- **2026-10** : Migration session HTTP → JWT stateless (`SessionCreationPolicy.STATELESS`)
- **2026-10** : Revue architecturale — threads manuels remplacés par `@Async`, `GlobalExceptionHandler` ajouté, cache `IpResolverService` limité (LRU 500 entrées), `@Valid` appliqué sur les DTOs principaux
- **2026-10** : Refactoring schedulers — toutes les propriétés cron regroupées sous `dmarc.scheduler.*` ; variables d'environnement renommées (`SCHEDULER_CHECK_MAILBOX`, `SCHEDULER_WEEKLY_REPORT`, `SCHEDULER_BLACKLIST`, `SCHEDULER_DNS_CHECK`) ; correction des propriétés de test manquantes dans `DmarcDashboardApiApplicationTests`
