# Essentiel — launcher Android minimaliste

Écran d'accueil sobre pour moins de distractions : date, météo, prochain événement de l'agenda,
8 boutons essentiels, une courte liste texte d'apps choisies et des limites de temps strictes.

Testé pour : ASUS Zenfone 5Z (Z01RD), Android 10.

## Obtenir l'APK (sans Android Studio)

1. Sur github.com, crée un nouveau dépôt (ex. `essentiel`), **public** ou privé.
2. Envoie tous les fichiers de ce dossier dans le dépôt, **y compris le dossier caché `.github`**
   (le plus simple : GitHub Desktop, ou la commande `git push`).
3. Onglet **Actions** : la compilation démarre toute seule (environ 5 minutes).
4. Onglet **Releases** (à droite sur la page du dépôt) : télécharge `Essentiel.apk` depuis le téléphone.
5. Ouvre le fichier, autorise « Installer des applis inconnues » pour ton navigateur, installe.

Chaque nouvelle modification envoyée sur `main` produit une nouvelle version, qui s'installe
par-dessus l'ancienne (la clé de signature est fixe : `app/essentiel.keystore`).

## Premier lancement

1. Ouvre Essentiel, appuie longuement sur la date → **réglages**.
2. « Choisir Essentiel comme launcher » → sélectionne Essentiel.
3. Active les autorisations : Agenda, Position, Accès aux données d'utilisation,
   Afficher par-dessus les autres apps (les deux dernières servent au blocage strict).
4. Dans « Mes apps et limites », touche les apps à mettre dans ta liste et fixe leurs limites.

## Utilisation

- **Glisser vers le haut** : liste « mes apps » (texte, sans icônes). Glisser vers le bas pour fermer.
- **Appui long sur la date** : réglages.
- **Boutons** : chaque bouton ouvre l'app du système par défaut ; tu peux en choisir une autre dans les réglages.
- **Limites** : quand une app atteint sa limite du jour, elle est fermée et reste bloquée jusqu'à minuit.
  Réduire une limite s'applique tout de suite, l'augmenter ou la retirer seulement le lendemain.

## Apps intégrées (version 0.2)

- **Téléphone** : clavier, appels récents, écran d'appel Essentiel (entrant / en cours, muet,
  haut-parleur, attente, clavier pendant l'appel). Réglages › « App Téléphone par défaut ».
- **Messages** : SMS (lecture, envoi, réponse depuis la notification) et affichage des MMS reçus.
  Pas de RCS (impossible hors Google Messages). En app SMS par défaut, les nouveaux MMS ne sont pas encore reçus.
- **Contacts** : liste, recherche, favoris, fiche, création, modification, suppression.
- **Photo** : photo et vidéo, flash, caméra avant / arrière.
- **Galerie** : photos et vidéos, visionneuse, partage, suppression.
- **Agenda** : vue du mois, événements du jour, création / modification / suppression.
- **Rappels** : un texte + une heure, notification avec « Fait » et « Dans 10 min ».

## Nouveau en 0.3

- **Horloge** : réveils (répétition par jour, « répéter dans 10 min », sonne écran verrouillé),
  minuteur, chronomètre avec tours.
- **Mémos vocaux** : enregistrement, lecture, renommer, partager, supprimer.
- **Carte** (essai) : carte OpenFreeMap / OpenStreetMap repeinte aux couleurs d'Essentiel, ta position,
  recherche d'adresses (Photon), itinéraires voiture / vélo / à pied (OSRM) avec guidage vocal simple.
  Appui long sur la carte = choisir un point. Nécessite Internet (pas de carte hors ligne).

Mémos vocaux et Carte sont dans la liste « mes apps » (glisser vers le haut). On peut aussi les mettre
à la place d'un bouton de l'accueil : Réglages › Boutons.

## Nouveau en 0.4

- **Favoris de l'accueil** : appui long sur une case → nombre de cases (4, 8, 12), ordre (‹ ›), app de chaque case.
- **Écran de verrouillage Essentiel** (Réglages › Écran de verrouillage) : heure, date, météo, prochain réveil,
  événement et rappel, lampe torche, photo. Glisser vers le haut ; le code Android reste demandé ensuite.
  Nécessite « Afficher par-dessus les autres apps ».
- **Appels** : choix de la SIM (double SIM) quand Essentiel est l'app Téléphone, lancement d'appel plus fiable.
- **Messages** : nouvelle mise en page (bulles groupées, séparateurs de jour, barre de saisie).
- **Carte** : points d'intérêt affichés et touchables, écran de recherche, catégories (restaurants, boulangeries…),
  favoris, récents, partage d'un lieu, heure d'arrivée, zoom +/–.

## Nouveau en 0.5

- **Carte** : plusieurs itinéraires au choix (Valhalla / OpenStreetMap), options « éviter autoroutes / péages / ferries »,
  localisation précise (service de Google), flèche qui suit le sens du téléphone, guidage qui zoome et s'oriente,
  voix activable / coupable, trafic en temps réel avec une clé TomTom gratuite (roue dentée dans la barre de recherche).
- **Agenda** : ajout corrigé (choix de l'agenda, agenda « Essentiel » créé si besoin, vraie raison en cas d'erreur), nouvelle présentation.
- **Galerie** : tri par jour, date + lieu en haut, infos de la photo (appareil, réglages, lieu) en glissant,
  éditeur (recadrage, rotation, filtres, luminosité / contraste / saturation / chaleur) qui enregistre une copie.
- **Appareil photo** : nouvelle interface, pincer pour zoomer, boutons 0,5 / 1 / 2 / 5, mise au point au toucher,
  grille, retardateur 3 / 10 s.
- **Icônes** : les apps installées ont leur icône ; modifiable (icône Essentiel ou initiale) en modifiant la case.
- **Rappels, Contacts, Mémos, Horloge, Téléphone** : nouvelle présentation. Plus de flèche retour (geste retour d'Android).

## Plus tard

Réception et envoi des MMS, notes, carte hors ligne.
