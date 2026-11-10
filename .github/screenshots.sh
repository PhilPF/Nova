#!/usr/bin/env bash
# Takes screenshots of the debug build with the sample library (MainActivity's demo mode).
set -euo pipefail
PACKAGE=com.philpf.nova
adb install -r Nova.apk
mkdir -p screenshots
adb shell settings put global hide_error_dialogs 1
# No "Viewing full screen" tip over the full-screen shots.
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell input keyevent KEYCODE_HOME
sleep 8

open_app() {
  adb shell am force-stop "$PACKAGE"
  adb shell am start -W -n "$PACKAGE/.MainActivity" --ez demo true "$@"
}

capture() {
  adb exec-out screencap -p > "screenshots/$1.png"
}

shot() {
  local name=$1 wait=$2
  shift 2
  open_app "$@"
  sleep "$wait"
  capture "$name"
}

# The sample library goes into the app's own files (run-as works for debug builds).
# Piped as a tar stream, as the app may not read the shell's folders itself.
adb push demo /data/local/tmp/nova-demo
adb shell "tar -cf - -C /data/local/tmp/nova-demo . | run-as $PACKAGE sh -c 'mkdir -p files/Demo && tar -xf - -C files/Demo'"
adb shell "run-as $PACKAGE ls -R files/Demo"
shot library-dark 20 --es theme dark
shot library-light 6 --es theme light
shot albums-dark 6 --es theme dark --es tab albums
shot artists-light 6 --es theme light --es tab artists
shot playlists-dark 6 --es theme dark --es tab playlists
shot video-tracklist-dark 6 --es theme dark --es tab playlists --es open mix
shot artist-dark 6 --es theme dark --es open artist
shot album-light 6 --es theme light --es open album
shot menu-dark 6 --es theme dark --es open menu
shot tracklist-editor-light 6 --es theme light --es open tracklist
shot settings-light 6 --es theme light --es open settings
shot details-dark 6 --es theme dark --es open details
# Playing the long video from its second song: the video in the art's place, the song inside it.
shot player-video-dark 10 --es theme dark --es play mix --es open player
adb shell input swipe 540 2000 540 600 400
sleep 2
capture player-video-tracklist-dark
shot player-video-light 10 --es theme light --es play mix --es open player
shot fullscreen 12 --es theme dark --es play mix --es open fullscreen
adb shell input tap 1200 540
sleep 1
capture fullscreen-controls
shot player-album-dark 10 --es theme dark --es play album --es open player
shot player-art-dark 10 --es theme dark --es play mix --es open art
# Up next, below the player: swipe up for all of it.
shot player-up-next-dark 10 --es theme dark --es play album --es open player
adb shell input swipe 540 2000 540 600 400
sleep 2
capture player-up-next-dark
# Swiping the library sideways goes from Songs to Albums.
shot swipe-tabs-dark 8 --es theme dark
adb shell input swipe 900 1400 150 1400 250
sleep 2
capture swipe-tabs-dark
# Swiping the player's art to the left: the next song slides in.
shot player-swiped-dark 10 --es theme dark --es play album --es open player
adb shell input swipe 800 800 150 800 200
sleep 3
capture player-swiped-dark
# Swiping the mini player to the left: the next song.
shot mini-player-swiped-dark 10 --es theme dark --es play album
adb shell input swipe 800 2180 200 2180 200
sleep 3
capture mini-player-swiped-dark
# The mini player over the library.
shot mini-player-dark 10 --es theme dark --es play album

ls -l screenshots
