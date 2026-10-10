# EterVelocityLib

Le socle commun des plugins Eter du **proxy Velocity** (4.2+, Java 25), comme EterLib côté Paper. Document
développeur, à tenir à jour avec le code. Utilisé par **EterVelocityLobby**, **EterVelocityResource**, **EterVelocityModeration**, **EterVelocityBroadcast** et **EterTab** (côté proxy).

## Contenu

- **`core`** : `Config` (config.yml d'un plugin, copiée du jar au premier démarrage), `Lang` (lang/<locale>.yml du
  plugin, repli sur les textes communs d'EterVelocityLib puis sur la langue par défaut), `YamlFiles` (SnakeYAML
  fourni par Velocity). `Sql` : la base MariaDB/MySQL (pilote MariaDB téléchargé dans `libs/` et ajouté au classpath du
  plugin), `query` / `execute` avec paramètres, bloquant (tâche de fond). La base du réseau :
  `EterVelocityLib.get().database()`, ouverte au premier appel avec les accès de `plugins/etervelocitylib/EterLib-config.yml`
  (le même fichier que pour les serveurs créés) ; l'orchestrateur ouvre la sienne avec la config de sa famille.
- **`helper/Messages`** : MiniMessage avec la palette et le préfixe communs ; `legacy(texte)` pour les endroits qui
  n'acceptent qu'un texte simple (MOTD), réglés UNE fois dans le `config.yml`
  d'EterVelocityLib (à garder identiques à `EterLib/config.yml`). Obtenu par
  `EterVelocityLib.get().messages(MonPlugin.class, dataDirectory, logger)`.
- **`orchestrator`** : le moteur des serveurs jetables sur Pterodactyl, par **famille** (`eterlobby`,
  `eterresource`…). Chaque plugin crée son `ServerPool` avec ses règles et enregistre `PoolCommand` sous son propre nom.

## Orchestrateur

- **Modèle facultatif** (`template/` du plugin) : `template.zip` ou `template.tar.gz` (ou `lobby.*`, ancien nom) :
  il fournit TOUT le serveur (fichiers, plugins tiers et leur config). **Sans modèle**, le moteur écrit lui-même
  `eula.txt`, `server.properties` (`online-mode=false`, port, `max-players` = `capacity`, + `orchestrator.server-properties`)
  et `config/paper-global.yml` (secret Velocity lu dans `forwarding-secret-file` de `velocity.toml`, ou
  `VELOCITY_FORWARDING_SECRET`) ; Paper génère le reste. Config d'EterLib (`%server%`, `%display%`) :
  **la seule du proxy**, `plugins/etervelocitylib/EterLib-config.yml` (un `template/EterLib-config.yml` est ignoré, avec
  un avertissement).
  Les plugins sont la **dernière release GitHub** de chacun (`orchestrator.plugins`), Eter ou tiers (ex :
  `MilkBowl/Vault`, jar sans version `Vault.jar`) ; jars gardés dans
  `cache/<dépôt>/<tag>/` ; le jeton GitHub ne part qu'à l'API, jamais au téléchargement des jars.
- **Création** : ligne `CREATING` en base **avant** le panel (une création interrompue reste retrouvable) → serveur
  créé par **déploiement automatique** (`location-id`, `port-range` facultatif ; œuf, propriétaire dédié, identifiant
  externe `<famille>:<nom>`) → attente de l'installation → envoi de l'archive (taille reçue vérifiée, jusqu'à 3 envois),
  décompression → jars dans `plugins/` →
  config d'EterLib → démarrage → ajout à Velocity → `ACTIVE` dès qu'il répond. Échec : suppression (sûre), puis 5 min
  de pause avant une autre création (pas de boucle si la base ou le panel sature).
- **Règles** (`servers:` du plugin, toutes les 30 s, un seul fil) : au moins `minimum` serveurs **à jour** ; un de plus
  quand ils sont remplis à `scale-up-at` (au plus `maximum`) ; une ancienne version (empreinte de l'archive, de
  `EterLib-config.yml` et des tags des plugins, relue toutes les 10 min) ou un serveur plus vieux que
  `max-lifetime-hours` (0 = jamais) est vidé dès que les serveurs à jour suffisent pour les joueurs présents ; un
  serveur en trop vide depuis `idle-minutes`, ou qui ne répond plus depuis 5 min, est vidé. **Vidé** (`DRAINING`) :
  plus de nouveaux joueurs, supprimé une fois vide ou après `drain-timeout-minutes` (joueurs envoyés au repli donné
  par le plugin, sinon renvoyés au lobby par EterVelocityLobby).
- **Sécurité** (le panel héberge aussi les serveurs des clients) :
  - table `<famille>_servers` = seule source de vérité ; un serveur n'est supprimé que s'il y figure, appartient à
    `owner-user-id` ET porte l'identifiant externe `<famille>:<nom>` ; sinon erreur dans la console, rien n'est touché ;
  - serveurs `<famille>:` du panel absents de la table : seulement signalés, jamais supprimés ;
  - `dry-run` (par défaut) : écrit ce qu'il ferait, sans rien faire ;
  - panel en `https://` obligatoire ; clés et mots de passe jamais écrits dans la console (erreurs YAML sans la ligne
    fautive, pas de trace complète).
- **Base** : celle d'EterVelocityLib (`database()`, même fichier) ; pilote MariaDB téléchargé au premier démarrage dans `libs/` et
  ajouté au proxy (`addToClasspath`) : rien d'embarqué. À la suppression, seule sa ligne de
  `<famille>_servers` est retirée : chaque plugin nettoie ses propres tables (EterLib oublie un serveur muet depuis un
  jour ; EterHub et EterResource effacent leurs lignes trop vieilles).

## Technique

- Plugin Velocity (`@Plugin(id = "etervelocitylib")`), initialisé dans son constructeur : prêt avant les plugins qui en
  dépendent (`@Dependency(id = "etervelocitylib")`).
- Compilé par les plugins via JitPack : `compileOnly("com.github.Eternom:EterVelocityLib:<tag>")` (pousser le tag
  d'EterVelocityLib AVANT ceux des plugins qui en dépendent).
- La version est aussi écrite dans `@Plugin` : à garder identique à `gradle.properties`.
