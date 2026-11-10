#!/usr/bin/env bash
# Makes a small sample library with ffmpeg, for the screenshots: two albums with covers, a long
# video with chapters (as yt-dlp --embed-chapters writes them), a live recording whose tracklist
# is only in its description, and a music video. All names are made up.
set -euo pipefail
OUT=${1:-demo}
rm -rf "$OUT" && mkdir -p "$OUT/Luma - Glass Harbor" "$OUT/Neon Fields - Afterglow" "$OUT/Videos"
TMP=$(mktemp -d)
q() { ffmpeg -loglevel error -y "$@"; }

# A soft chord: three sines and a little tremolo.
tone() { # duration, base frequency, output
  q -f lavfi -i "aevalsrc=0.18*sin(2*PI*$2*t)+0.12*sin(2*PI*$2*1.25*t)+0.1*sin(2*PI*$2*1.5*t)*(0.6+0.4*sin(2*PI*0.5*t)):s=44100:d=$1" "$3"
}
cover() { # colour 1, colour 2, output
  q -f lavfi -i "gradients=s=600x600:c0=$1:c1=$2:x0=0:y0=0:x1=600:y1=600:nb_colors=2:d=1" -frames:v 1 "$3"
}

cover 0x2b5cff 0x9b4dff "$TMP/harbor.png"
cover 0xff6a3d 0xffc93d "$TMP/afterglow.png"

i=1
for t in "Glass Harbor" "Lanterns" "Undertow" "Morning Ferry"; do
  tone 40 $((196 + i * 22)) "$TMP/h$i.wav"
  q -i "$TMP/h$i.wav" -i "$TMP/harbor.png" -map 0 -map 1 -c:a libmp3lame -b:a 64k -c:v mjpeg -id3v2_version 3 \
    -metadata title="$t" -metadata artist="Luma" -metadata album="Glass Harbor" -metadata album_artist="Luma" \
    -metadata track="$i/4" -metadata date=2024 -disposition:v attached_pic "$OUT/Luma - Glass Harbor/0$i $t.mp3"
  i=$((i + 1))
done

i=1
for t in "Afterglow" "Static Bloom" "Weekend Satellites"; do
  tone 45 $((260 + i * 30)) "$TMP/a$i.wav"
  q -i "$TMP/a$i.wav" -i "$TMP/afterglow.png" -map 0 -map 1 -c:a aac -b:a 64k -c:v png -disposition:v attached_pic \
    -metadata title="$t" -metadata artist="Neon Fields" -metadata album="Afterglow" -metadata album_artist="Neon Fields" \
    -metadata track="$i/3" -metadata date=2023 "$OUT/Neon Fields - Afterglow/0$i $t.m4a"
  i=$((i + 1))
done

# A long video with six songs as chapters, and its tracklist in the description too.
cat > "$TMP/mix.txt" <<'META'
;FFMETADATA1
title=Night Drive Mix
artist=Aurora Drive
description=Six songs for the road.\
\
0:00 Luma - Glass Harbor\
0:30 Neon Fields - Afterglow\
1:00 Orbital Kids - Paper Moons\
1:30 Sera Vale - Slow Comet\
2:00 Luma - Tidelines\
2:30 Halcyon Run - Last Train Home
META
n=0
for t in "Luma - Glass Harbor" "Neon Fields - Afterglow" "Orbital Kids - Paper Moons" "Sera Vale - Slow Comet" "Luma - Tidelines" "Halcyon Run - Last Train Home"; do
  printf '[CHAPTER]\nTIMEBASE=1/1000\nSTART=%d\nEND=%d\ntitle=%s\n' $((n * 30000)) $(((n + 1) * 30000)) "$t" >> "$TMP/mix.txt"
  n=$((n + 1))
done
tone 180 174 "$TMP/mix.wav"
q -f lavfi -i "gradients=s=640x360:c0=0x101a5c:c1=0x7a2cff:c2=0xff3d8b:c3=0x00c2ff:speed=0.015:r=24:d=180" -i "$TMP/mix.wav" -i "$TMP/mix.txt" \
  -map 0 -map 1 -map_metadata 2 -map_chapters 2 -c:v libx264 -preset veryfast -crf 30 -pix_fmt yuv420p -c:a aac -b:a 64k -shortest \
  "$OUT/Videos/Aurora Drive - Night Drive Mix [nightdrive1].mp4"

# A live recording whose tracklist is only written in its description.
tone 150 147 "$TMP/live.wav"
q -i "$TMP/live.wav" -c:a libopus -b:a 48k -metadata title="Live at the Observatory" -metadata artist="Orbital Kids" \
  -metadata description=$'Recorded live.\n\nSetlist:\n00:00 Intro\n00:25 Orbital Kids - Paper Moons\n01:10 Orbital Kids - Gravity Well\n01:50 Orbital Kids - Northern Lights' \
  "$OUT/Orbital Kids - Live at the Observatory.opus"

# A music video, its artist in its name only.
tone 60 233 "$TMP/mv.wav"
q -f lavfi -i "gradients=s=640x360:c0=0xff8a00:c1=0xe52e71:speed=0.03:r=24:d=60" -i "$TMP/mv.wav" \
  -map 0 -map 1 -c:v libx264 -preset veryfast -crf 30 -pix_fmt yuv420p -c:a aac -b:a 64k -shortest \
  "$OUT/Videos/Halcyon Run - Last Train Home (Official Video).mkv"

rm -rf "$TMP"
find "$OUT" -type f | sort
du -sh "$OUT"
