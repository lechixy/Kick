Source: https://static.kick.com/tier/animation/level-{tier}.webm
Tiers: 40, 50, 60, 70, 80, 90. Higher levels reuse tier 90.

These are the original animations transcoded to looping Animated WebP, preserving VP9 alpha.
Android's ImageDecoder plays them without an additional video player or audio track.
The 688×306 source is displayed at 216 dp wide behind the 64 dp avatar.

Recreate each asset with FFmpeg (libvpx-vp9 is required to decode alpha):

    ffmpeg -c:v libvpx-vp9 -i level-40.webm -an -vf scale=516:230:flags=lanczos -c:v libwebp_anim -lossless 0 -q:v 78 -compression_level 6 -loop 0 level-40.webp
