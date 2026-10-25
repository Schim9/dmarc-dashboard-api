# CLAUDE.md — dmarc-dashboard-api

Backend Spring Boot du projet DMARC Dashboard.

**Repo GitHub** : https://github.com/Schim9/dmarc-dashboard-api
**Déploiement** : Pi Apps (appsPi, 192.168.0.11) — voir skill `/info-pi-apps`
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

Session HTTP + cookie (pas de JWT). Un seul utilisateur admin en mémoire (`InMemoryUserDetailsManager`).
Le CSRF est désactivé — à réactiver si l'appli devient publique ou multi-utilisateurs.

## Points de vigilance connus (à corriger)

- **Threads manuels** dans `BlacklistController` et `DnsCheckController` (`new Thread(...)`) → à remplacer par `@Async`
- **Aucun `@ControllerAdvice`** — les exceptions non catchées retournent un 500
- **`InterventionStatus.valueOf()`** non protégé → erreur 400 si valeur invalide envoyée par le client
- **Cache `IpResolverService`** illimité en mémoire (ConcurrentHashMap sans éviction)
- **Aucune validation Bean Validation** (`@Valid`) sur les DTOs de requête

## Historique des décisions

- **2026-10** : Suppression du mode démo (`dmarc.demo.mode`) — fonctionnalité retirée définitivement
- **2026-10** : Revue sécurité pré-GitHub — credentials retirés de `application.yml`, `.gitignore` créé
