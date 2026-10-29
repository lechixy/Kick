Source: https://static.kick.com/rewards/streaks-battery.webm

This is the original streak battery animation transcoded to looping Animated WebP, preserving VP9 alpha.
Android's ImageDecoder plays it without an additional video player or audio track.

Recreate asset with FFmpeg:
    ffmpeg -c:v libvpx-vp9 -i streaks-battery.webm -an -vf scale=300:300:flags=lanczos -c:v libwebp_anim -lossless 0 -q:v 78 -compression_level 4 -loop 0 streaks-battery.webp
