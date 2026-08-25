#!/bin/bash

# Resolve the directory this script lives in, regardless of cwd or symlinks
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$script_dir" || exit 1

# Note: the notification small icon is NOT generated here. Android tints
# the small icon and uses only its alpha, so a colour PNG shows as a white
# blob. app/src/main/res/drawable/ic_notification.xml is a hand-maintained
# vector carrying the same two paths as icon.svg - update it by hand if
# the artwork changes.

mkdir -p output

inkscape icon.svg -w 48   -h 48   -o ../app/src/main/res/mipmap-mdpi/ic_launcher.png
inkscape icon.svg -w 72   -h 72   -o ../app/src/main/res/mipmap-hdpi/ic_launcher.png
inkscape icon.svg -w 96   -h 96   -o ../app/src/main/res/mipmap-xhdpi/ic_launcher.png
inkscape icon.svg -w 144  -h 144  -o ../app/src/main/res/mipmap-xxhdpi/ic_launcher.png
inkscape icon.svg -w 192  -h 192  -o ../app/src/main/res/mipmap-xxxhdpi/ic_launcher.png

cp ../app/src/main/res/mipmap-mdpi/ic_launcher.png \
   ../app/src/main/res/mipmap-mdpi/ic_launcher_round.png

cp ../app/src/main/res/mipmap-hdpi/ic_launcher.png \
   ../app/src/main/res/mipmap-hdpi/ic_launcher_round.png

cp ../app/src/main/res/mipmap-xhdpi/ic_launcher.png \
   ../app/src/main/res/mipmap-xhdpi/ic_launcher_round.png

cp ../app/src/main/res/mipmap-xxhdpi/ic_launcher.png \
   ../app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png

cp ../app/src/main/res/mipmap-xxxhdpi/ic_launcher.png \
   ../app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png