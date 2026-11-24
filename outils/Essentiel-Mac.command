#!/bin/bash
# =============================================================================
#  Essentiel — réglages avancés du téléphone depuis un Mac
#  Sans root : rien n'est cassé, tout se défait avec ce même script.
#  Lancement : ouvrir Terminal, taper « bash » + espace, glisser ce fichier, Entrée.
# =============================================================================

PKG="fr.essentiel.launcher"
DIR="$HOME/.essentiel-adb"
LIST_ON_PHONE="/sdcard/Android/data/$PKG/files/nettoyage.txt"
REMOVED="$DIR/apps-retirees.txt"

orange() { printf "\033[38;5;202m%s\033[0m\n" "$1"; }
gris()   { printf "\033[90m%s\033[0m\n" "$1"; }

mkdir -p "$DIR"
touch "$REMOVED"

# ---------- 1. Outils Android (adb) ----------
ADB="$(command -v adb)"
if [ -z "$ADB" ]; then
  ADB="$DIR/platform-tools/adb"
  if [ ! -x "$ADB" ]; then
    orange "Téléchargement des outils Android officiels de Google (une seule fois)…"
    curl -L --fail -o "$DIR/pt.zip" "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip" || { echo "Échec du téléchargement."; exit 1; }
    unzip -q -o "$DIR/pt.zip" -d "$DIR" && rm -f "$DIR/pt.zip"
  fi
fi

sh_() { "$ADB" shell "$@" < /dev/null 2>&1 | tr -d '\r'; }

# ---------- 2. Téléphone ----------
clear
orange "ESSENTIEL · mode avancé"
echo
echo "Branche le téléphone en USB (débogage USB activé)."
echo "Si le téléphone demande « Autoriser le débogage USB ? », coche « Toujours » puis OK."
echo
"$ADB" start-server >/dev/null 2>&1
"$ADB" wait-for-device
MODEL="$(sh_ getprop ro.product.model)"
ANDROID="$(sh_ getprop ro.build.version.release)"
echo "Téléphone trouvé : $MODEL (Android $ANDROID)"

if ! sh_ pm list packages "$PKG" | grep -q "package:$PKG\$"; then
  echo
  echo "Essentiel n'est pas installé sur ce téléphone. Installe l'APK d'abord."
  exit 1
fi

# ---------- 3. Actions ----------
grant_all() {
  echo
  orange "Autorisations pour Essentiel…"
  sh_ pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS && echo "  ✓ mode avancé (barre d'état, position)"
  sh_ appops set "$PKG" WRITE_SETTINGS allow          && echo "  ✓ luminosité et rotation"
  sh_ appops set "$PKG" SYSTEM_ALERT_WINDOW allow     && echo "  ✓ affichage par-dessus les apps"
  sh_ appops set "$PKG" GET_USAGE_STATS allow         && echo "  ✓ temps d'écran (limites)"
  sh_ cmd notification allow_listener "$PKG/$PKG.control.NotifListener" && echo "  ✓ notifications"
  sh_ cmd notification allow_dnd "$PKG"               && echo "  ✓ Ne pas déranger"
  sh_ pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION && echo "  ✓ position précise"
  echo
  echo "Terminé. Rouvre Essentiel → Réglages → Système : tout doit être coché."
}

remove_apps() {
  echo
  LIST="$(sh_ cat "$LIST_ON_PHONE")"
  if [ -z "$LIST" ] || echo "$LIST" | grep -q "No such file"; then
    echo "Aucune liste trouvée."
    echo "Sur le téléphone : Essentiel → Réglages → Système → Nettoyer → coche les apps → Enregistrer."
    return
  fi
  orange "Apps qui vont disparaître du téléphone :"
  echo "$LIST" | while read -r p; do [ -n "$p" ] && echo "  • $p"; done
  echo
  read -r -p "On y va ? (o/n) " ok
  [ "$ok" = "o" ] || [ "$ok" = "O" ] || { echo "Annulé."; return; }
  echo "$LIST" | while read -r p; do
    [ -z "$p" ] && continue
    [ "$p" = "$PKG" ] && continue
    r="$(sh_ pm uninstall -k --user 0 "$p")"
    if echo "$r" | grep -q "Success"; then
      echo "  ✓ $p"
      grep -qx "$p" "$REMOVED" || echo "$p" >> "$REMOVED"
    else
      echo "  ✗ $p ($r)"
    fi
  done
  echo
  gris "Liste des apps retirées gardée dans $REMOVED (pour les remettre plus tard)."
}

restore_one() {
  echo
  if [ ! -s "$REMOVED" ]; then echo "Aucune app retirée par ce script sur ce Mac."; fi
  nl -w2 -s'. ' "$REMOVED" 2>/dev/null
  echo
  read -r -p "Numéro de l'app à remettre (ou nom complet du paquet) : " c
  if [[ "$c" =~ ^[0-9]+$ ]]; then p="$(sed -n "${c}p" "$REMOVED")"; else p="$c"; fi
  [ -z "$p" ] && { echo "Rien à faire."; return; }
  r="$(sh_ cmd package install-existing "$p")"
  echo "$r"
  if echo "$r" | grep -qi "installed"; then
    grep -vx "$p" "$REMOVED" > "$REMOVED.tmp"; mv "$REMOVED.tmp" "$REMOVED"
  fi
}

restore_all() {
  echo
  [ ! -s "$REMOVED" ] && { echo "Aucune app à remettre."; return; }
  while read -r p; do
    [ -z "$p" ] && continue
    echo "  $(sh_ cmd package install-existing "$p")"
  done < "$REMOVED"
  : > "$REMOVED"
  echo "Toutes les apps sont revenues."
}

reset_statusbar() {
  sh_ settings delete secure icon_blacklist >/dev/null
  echo "Barre d'état remise d'origine (redémarre le téléphone si une icône manque encore)."
}

while true; do
  echo
  orange "Que veux-tu faire ?"
  echo "  1. Donner toutes les autorisations à Essentiel (à faire en premier)"
  echo "  2. Retirer les apps choisies dans Essentiel → Système → Nettoyer"
  echo "  3. Remettre une app retirée"
  echo "  4. Remettre toutes les apps retirées"
  echo "  5. Remettre la barre d'état d'origine"
  echo "  q. Quitter"
  read -r -p "> " choix
  case "$choix" in
    1) grant_all ;;
    2) remove_apps ;;
    3) restore_one ;;
    4) restore_all ;;
    5) reset_statusbar ;;
    q|Q) echo "À bientôt."; exit 0 ;;
    *) echo "Choix inconnu." ;;
  esac
done
