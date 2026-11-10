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

## Plus tard

Notes, agenda, to-do, messages et téléphone intégrés au launcher.
