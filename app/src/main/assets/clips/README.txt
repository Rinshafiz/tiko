Transparent animated WebP clips (320 px, all on one common scale).
Used by the app: idle, idle_happy, walk (looping stride, faces right), sit (turn and sit down after walking in),
mood_judge, mood_angry, mood_excited, mood_sad, mood_happy. Optional extras: look, groom, roll, stretch, blink.
Missing clips fall back to still images with simple motion. meta.json: v = walking speed in clip pixels per second.
Make them with: python3 tools/make_clips.py clips.json app/src/main/assets/clips
