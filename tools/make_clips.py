#!/usr/bin/env python3
"""Green-screen videos -> transparent animated WebP clips that all share ONE scale, so the pet
never changes size or look when one clip hands over to another.

Usage:  python3 tools/make_clips.py clips.json app/src/main/assets/clips
clips.json maps clip names to videos, e.g.
  {"idle":"idle.mp4","idle_happy":"happy.mp4","entrance":"walk_in.mp4","mood_judge":"judge.mp4",
   "mood_angry":"angry.mp4","mood_excited":"excited.mp4","mood_sad":"sad.mp4","mood_happy":"smile.mp4"}
Also writes "walk" (a looping stride cut from the entrance clip) and meta.json.
Needs: ffmpeg, numpy, scipy, Pillow.  Any shade of green works."""
import sys, os, json, glob, tempfile, subprocess
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

S, FPS, LO, HI = 320, 18, 22.0, 70.0
TRACKED = {"entrance"}                       # clips where the tiger walks: keep him in place in the frame
REF = {"entrance": (0.75, 1.0), "mood_angry": (0.0, 0.4)}   # frames where he faces the camera
USE = {"mood_angry": (0.07, 0.52)}           # only the part where he still faces you (he turns away later in the video)
PINGPONG = {"mood_angry"}                    # play forward then backward: a calm hold that never turns away
LOOP = {"idle", "idle_happy", "mood_judge", "mood_excited", "mood_sad", "mood_happy"}


def frames_of(video):
    tmp = tempfile.mkdtemp()
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', video, '-vf', f'fps={FPS},scale=-2:720',
                    tmp + '/f%04d.png'], check=True)
    return sorted(glob.glob(tmp + '/f*.png'))


def key(path):
    a = np.asarray(Image.open(path).convert('RGB')).astype(np.float32)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    al = np.clip((HI - (g - np.maximum(r, b))) / (HI - LO), 0, 1)        # how un-green is this pixel
    lab, n = ndi.label(al > 0.5)
    if n:
        sz = ndi.sum(np.ones_like(lab), lab, range(1, n + 1))
        al *= ndi.binary_dilation(np.isin(lab, 1 + np.where(sz >= 0.04 * sz.max())[0]), iterations=3)  # drop sparkles
    al = ndi.gaussian_filter(al, 0.7)
    a[..., 1] = np.minimum(g, np.maximum(r, b))                           # remove green spill
    return np.dstack([np.clip(a, 0, 255), al * 255]).astype(np.uint8)


def stats(rgba):
    m = rgba[..., 3] > 127
    ys, xs = np.where(m)
    if not len(xs): return None
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    rows = m[y0:y0 + max(2, int((y1 - y0) * .5))]
    cols = np.arange(m.shape[1])
    mn = np.where(rows, cols, 10 ** 6).min(1); mx = np.where(rows, cols, -1).max(1)
    ext = (mx - mn)[rows.any(1)]
    return dict(x0=x0, x1=x1, y0=y0, y1=y1, cx=xs.mean(), head=float(np.percentile(ext, 90)))


def main(spec_path, out_dir):
    spec = json.load(open(spec_path)); os.makedirs(out_dir, exist_ok=True)
    info = {}
    for name, video in spec.items():                       # pass 1: measure every clip
        fs = frames_of(video); st = [stats(key(f)) for f in fs]
        n = len(fs); a, b = USE.get(name, (0, 1)); lo, hi = int(n * a), max(int(n * b), int(n * a) + 2)
        ra, rb = REF.get(name, (0, 1)); ref = [s['head'] for s in st[int(n * ra):max(int(n * rb), int(n * ra) + 1)] if s]
        sel = [s for s in st[lo:hi] if s]
        ext_w = max(s['x1'] - s['x0'] for s in sel) if name in TRACKED else max(s['x1'] for s in sel) - min(s['x0'] for s in sel)
        ext_h = max(s['y1'] - s['y0'] for s in sel)
        info[name] = dict(files=fs, st=st, lo=lo, hi=hi, head=float(np.median(ref)), kfit=min((S - 24) / ext_w, (S - 28) / ext_h))
    target = min(i['kfit'] * i['head'] for i in info.values())     # one head size for every clip
    meta = dict(S=S, fps=FPS)
    for name, i in info.items():                           # pass 2: render
        k = target / i['head']; fs = i['files'][i['lo']:i['hi']]; st = i['st'][i['lo']:i['hi']]
        ybot = float(np.median([s['y1'] for s in st if s]))
        cxs = np.array([s['cx'] if s else np.nan for s in st]); cxs = np.where(np.isnan(cxs), np.nanmean(cxs), cxs)
        cxs = ndi.uniform_filter1d(cxs, 9, mode='nearest') if name in TRACKED else np.full(len(cxs), (min(s['x0'] for s in st if s) + max(s['x1'] for s in st if s)) / 2)
        box_w = int(round(S / k)); out = []
        for f, cx in zip(fs, cxs):
            im = Image.fromarray(key(f), 'RGBA').convert('RGBa')
            l, t = int(round(cx - box_w / 2)), int(round(ybot - (S - 4) / k))
            out.append(im.crop((l, t, l + box_w, t + box_w)).resize((S, S), Image.LANCZOS).convert('RGBA'))
        if name in TRACKED:                                # when does he stop walking, and how fast does he go?
            sp = np.abs(np.gradient(ndi.uniform_filter1d(np.array([s['cx'] for s in st]), 5, mode='nearest')))
            moving = np.where(sp > 0.25 * sp.max())[0]; w_end = int(moving.max()) if len(moving) else len(st) - 1
            w_start = int(moving.min()) if len(moving) else 0
            dist = (st[w_end]['cx'] - st[w_start]['cx']) * k
            meta['walk_end'] = int(w_end * 1000 / FPS); meta['len'] = int(len(out) * 1000 / FPS)
            meta['v'] = abs(dist) / max((w_end - w_start) / FPS, .1)
            small = [np.asarray(o.resize((64, 64))).astype(np.float32) for o in out]
            best = (1e9, 0, 12)
            for a0 in range(max(w_start, 4), max(w_start, w_end - 36)):
                for ln in range(12, 36):
                    if a0 + ln >= w_end: break
                    d = np.abs(small[a0] - small[a0 + ln]).mean()
                    if d < best[0]: best = (d, a0, ln)
            save(out[best[1]:best[1] + best[2]], os.path.join(out_dir, 'walk.webp'))
        if name in PINGPONG:
            out = out + out[-2:0:-1]
        elif name in LOOP and len(out) > 30:                 # cross-fade the end into the start: no jump when it repeats
            m = 12; L = len(out) - m
            pm = [np.asarray(o.convert('RGBa')).astype(np.float32) for o in out]
            loop = []
            for j in range(L):
                arr = pm[j] if j >= m else pm[j + L] * (1 - j / m) + pm[j] * (j / m)
                loop.append(Image.fromarray(arr.astype(np.uint8), 'RGBa').convert('RGBA'))
            out = loop
        save(out, os.path.join(out_dir, name + '.webp')); print(name, len(out), 'frames', 'k=%.3f' % k)
    json.dump(meta, open(os.path.join(out_dir, 'meta.json'), 'w')); print(meta)


def save(frames, path):
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=int(1000 / FPS), loop=0, quality=72, method=4)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
