# CyberWatch Mobile

CyberWatch Mobile est une application Android de veille cybersécurité pensée pour le téléphone.

## Ce que fait le projet

- Agrège plusieurs flux de cybersécurité.
- Scrape avec parcimonie des pages publiques quand aucun flux structuré n'est disponible.
- Normalise, déduplique et classe les informations.
- Met à jour `data/feed.json` automatiquement avec GitHub Actions.
- L'application Android récupère ce flux au lancement et en arrière-plan.
- Les nouvelles entrées peuvent déclencher une notification Android.
- Un workflow GitHub construit automatiquement un APK debug installable et le publie dans une Release GitHub nommée **CyberWatch Mobile - Latest APK**.

## Sources initiales

- CERT-FR : flux complet, alertes, menaces/incidents, avis et bulletins d'actualité.
- CISA Known Exploited Vulnerabilities (KEV).
- BleepingComputer : RSS + fallback de scraping léger de la page d'accueil.

Les sources sont configurées dans `collector/collect.py` et peuvent être étendues facilement.

## Installation sur Android

1. Ouvrir la page **Releases** de ce dépôt.
2. Télécharger `CyberWatch.apk`.
3. Autoriser temporairement l'installation depuis le navigateur / gestionnaire de fichiers Android si nécessaire.
4. Installer l'APK.
5. Autoriser les notifications pour recevoir les nouvelles alertes.

> L'APK produit ici est un APK debug signé automatiquement par Android/Gradle. Il est adapté à une installation personnelle hors Play Store. Pour une distribution publique, ajoute une clé de signature de production.

## Fréquence

Le collecteur GitHub est planifié toutes les 15 minutes. GitHub peut parfois décaler légèrement les tâches planifiées. Android WorkManager utilise également une périodicité minimale d'environ 15 minutes et Android peut retarder le travail en arrière-plan pour économiser la batterie.

Pour du push réellement instantané, la prochaine évolution naturelle est d'ajouter Firebase Cloud Messaging ou un petit backend webhook.

## Scraping responsable

Le collecteur :
- utilise un User-Agent explicite ;
- impose des timeouts ;
- ne contourne ni authentification ni protections ;
- privilégie RSS/API avant le scraping HTML ;
- limite le volume et la fréquence des requêtes.

## Développement local

Pré-requis : JDK 17, Android SDK 35 et Gradle 8.9.

```bash
gradle assembleDebug
```

L'APK local se trouve dans :
`app/build/outputs/apk/debug/app-debug.apk`

Pour tester le collecteur :

```bash
python -m pip install -r collector/requirements.txt
python collector/collect.py
```


## Interface 0.2

La version 0.2 introduit une refonte complète de l'interface mobile :
- tableau de bord synthétique avec volume, priorités et nombre de sources ;
- identité visuelle CyberWatch claire/sombre ;
- recherche et filtres enrichis avec compteurs ;
- cartes d'alerte hiérarchisées par gravité ;
- meilleure lisibilité des sources, dates et catégories ;
- états de chargement, erreur et recherche vide cohérents ;
- nouvelle icône adaptative Android.
