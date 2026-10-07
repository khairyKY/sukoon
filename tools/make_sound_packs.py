"""
Sukoon sound packs, synthesized (no samples, nothing licensed). Each pack has the same six roles:
urgent (urgent low, loops), low (loops), going_low, high, no_readings, reminder. Run with a pack name
to write it into res/raw as the app's sounds; with --preview DIR to write every pack there for listening.

    python tools/make_sound_packs.py --preview out/          # all packs, for the picker page
    python tools/make_sound_packs.py astral                  # install one pack into the app
"""
import os
import sys
import numpy as np
from scipy.io import wavfile
from scipy.signal import fftconvolve

RATE = 32000
RNG = np.random.default_rng(7)  # fixed: the same file every run
RAW = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res', 'raw')
ROLES = ['urgent', 'low', 'going_low', 'high', 'no_readings', 'reminder']
# The app's file per role (res/raw names, kept so stored choices stay valid).
APP_NAMES = {'urgent': 'sukoon_rise', 'low': 'sukoon_ripple', 'going_low': 'sukoon_drift', 'high': 'sukoon_warm', 'no_readings': 'sukoon_knock', 'reminder': 'sukoon_chime'}


def hz(semitones_from_a4):
    return 440.0 * 2 ** (semitones_from_a4 / 12)


def t_of(length):
    return np.arange(int(RATE * length)) / RATE


def env(length, attack=0.01, release=0.05, decay=None):
    """Soft attack, optional exponential decay, click-free end."""
    t = t_of(length)
    e = np.minimum(1, t / max(attack, 1e-4))
    if decay:
        e *= np.exp(-t * decay)
    e *= np.clip((length - t) / max(release, 1e-4), 0, 1)
    return e


def tone(freq, length, partials=((1, 1.0),), decay=None, attack=0.01, release=0.05, glide_to=None, vibrato=0.0, vib_rate=5.0, detune_cents=0.0):
    t = t_of(length)
    f = np.full_like(t, freq) if glide_to is None else freq * (glide_to / freq) ** (t / length)
    if vibrato:
        f = f * (1 + vibrato * np.sin(2 * np.pi * vib_rate * t))
    wave = np.zeros_like(t)
    for cents in ([0.0] if not detune_cents else [-detune_cents, detune_cents]):
        phase = 2 * np.pi * np.cumsum(f * 2 ** (cents / 1200)) / RATE
        for ratio, amp in partials:
            pd = 1.0 if decay is None else np.exp(-t * decay * (ratio - 1) * 0.35)  # higher partials fade sooner
            wave += amp * np.sin(phase * ratio) * pd
    return wave * env(length, attack, release, decay)


def fm(freq, length, ratio=2.0, index=2.0, index_decay=6.0, decay=4.0, attack=0.003, glide_to=None):
    """A bright FM blip (sci-fi interface sounds)."""
    t = t_of(length)
    f = np.full_like(t, freq) if glide_to is None else freq * (glide_to / freq) ** (t / length)
    mod = index * np.exp(-t * index_decay) * np.sin(2 * np.pi * np.cumsum(f * ratio) / RATE)
    return np.sin(2 * np.pi * np.cumsum(f) / RATE + mod) * env(length, attack, 0.03, decay)


def noise(length, color=0.98):
    """Soft filtered noise (one-pole low-pass): air, static, whoosh."""
    n = RNG.standard_normal(int(RATE * length))
    out = np.zeros_like(n)
    acc = 0.0
    for i, x in enumerate(n):
        acc = color * acc + (1 - color) * x
        out[i] = acc
    return out / (np.max(np.abs(out)) or 1)


def place(total, pieces):
    out = np.zeros(int(RATE * total))
    for start, wave in pieces:
        i = int(RATE * start)
        out[i:i + len(wave)] += wave[:max(0, len(out) - i)]
    return out


def reverb(wave, seconds=1.6, wet=0.35, bright=0.6):
    """Convolution with decaying, filtered noise: a big soft space."""
    n = int(RATE * seconds)
    t = np.arange(n) / RATE
    ir = RNG.standard_normal(n) * np.exp(-t * 4.0 / seconds)
    ir = np.convolve(ir, np.ones(int(2 + (1 - bright) * 12)) / (2 + (1 - bright) * 12), mode='same')
    ir[0] = 0
    dry = np.concatenate([wave, np.zeros(n)])
    tail = np.zeros_like(dry)
    full = fftconvolve(wave, ir)
    tail[:min(len(dry), len(full))] = full[:len(dry)]
    tail /= (np.max(np.abs(tail)) or 1)
    return dry * (1 - wet) + tail * wet * (np.max(np.abs(wave)) or 1)


def echo(wave, delay=0.28, feedback=0.45, repeats=5):
    d = int(RATE * delay)
    out = np.concatenate([wave, np.zeros(d * repeats)])
    for k in range(1, repeats + 1):
        out[d * k:d * k + len(wave)] += wave * feedback ** k
    return out


def loopable(wave, length):
    """Folds the tail past [length] back onto the start, so a looping file has no gap or jump."""
    n = int(RATE * length)
    out = wave[:n].copy()
    rest = wave[n:]
    while len(rest):
        out[:min(n, len(rest))] += rest[:n]
        rest = rest[n:]
    return out


def finish(wave, level, drive=1.6):
    """Gentle saturation for presence (louder without clipping), then the peak at [level]."""
    wave = wave / (np.max(np.abs(wave)) or 1)
    wave = np.tanh(drive * wave) / np.tanh(drive)
    return wave * level


# ---------------------------------------------------------------- packs

def astral():
    """Astral: celestial and calm. Shimmering bell partials, long starry reverb, slow motion."""
    bell = ((1, 1.0), (2.0, 0.5), (3.0, 0.22), (4.16, 0.12), (5.43, 0.06))
    def star(f, l=1.2, d=3.2):
        return tone(f, l, partials=bell, decay=d, attack=0.004, detune_cents=4)
    # urgent: a rising four-note beacon over a pulsing low drone, twice a loop. Unmistakable, still musical.
    beacon = [hz(19), hz(23), hz(26), hz(31)]
    drone = tone(hz(-14), 2.6, partials=((1, 1.0), (2, 0.3)), attack=0.05, release=0.05) * (0.55 + 0.45 * np.sin(2 * np.pi * 3.3 * t_of(2.6)) ** 2)
    urgent = place(2.6, [(0, drone * 0.35)] + [(rep * 1.3 + i * 0.11, star(f, 0.9, 4.5)) for rep in range(2) for i, f in enumerate(beacon)])
    urgent = loopable(reverb(urgent, 1.2, 0.3), 2.6)
    # low: a two-note call with echoes trailing into space, twice.
    low = place(3.4, [(0, star(hz(16), 1.4, 2.6)), (0.34, star(hz(11), 1.6, 2.4)), (1.7, star(hz(16), 1.4, 2.6)), (2.04, star(hz(11), 1.6, 2.4))])
    low = loopable(reverb(echo(low, 0.3, 0.35, 3), 1.6, 0.35), 3.4)
    # going low: a falling star, a sparkle gliding down an octave, then a soft landing note.
    fall = tone(hz(27), 2.0, partials=((1, 1), (2, 0.35), (3, 0.12)), glide_to=hz(15), attack=0.01, release=0.4, decay=0.9, vibrato=0.004, vib_rate=7)
    going = reverb(place(3.0, [(0, fall), (1.7, star(hz(15), 1.3, 2.4) * 0.7)]), 2.0, 0.45)
    # high: a low planet swell, a detuned fifth that rises and fades. Noticeable, never sharp.
    swell = tone(hz(-19), 3.2, partials=((1, 1), (1.5, 0.6), (2, 0.4), (3, 0.15)), attack=1.0, release=1.4, detune_cents=7, vibrato=0.002, vib_rate=0.8)
    high = reverb(place(3.4, [(0, swell), (0.6, star(hz(7), 1.6, 2.2) * 0.5), (1.1, star(hz(14), 1.6, 2.2) * 0.4)]), 2.0, 0.4)
    # no readings: a sonar ping into the dark, answered by nothing, twice.
    ping = tone(hz(14), 0.5, partials=((1, 1), (2.01, 0.2)), decay=7, attack=0.002)
    sonar = reverb(echo(place(2.6, [(0, ping), (1.3, ping * 0.8)]), 0.34, 0.5, 4), 1.5, 0.3)
    # reminder: a small constellation, pentatonic sparkles.
    sparkles = [hz(19), hz(24), hz(21), hz(28), hz(26)]
    remind = reverb(place(2.4, [(i * 0.13, star(f, 1.2, 3.4) * (0.9 - i * 0.08)) for i, f in enumerate(sparkles)]), 1.8, 0.4)
    return {'urgent': urgent, 'low': low, 'going_low': going, 'high': high, 'no_readings': sonar, 'reminder': remind}


def orbit():
    """Orbit: a calm spacecraft. FM chirps and console tones, clean and modern, a little echo."""
    # urgent: three rising chirps and a two-tone alert, twice a loop.
    def chirp(f0, f1, l=0.16):
        return fm(f0, l, ratio=1.5, index=1.6, index_decay=8, decay=6, glide_to=f1)
    alert = [(0, chirp(700, 1400)), (0.2, chirp(800, 1600)), (0.4, chirp(900, 1800)),
             (0.7, fm(hz(16), 0.32, ratio=2, index=1.2, decay=2.5)), (1.0, fm(hz(11), 0.32, ratio=2, index=1.2, decay=2.5))]
    urgent = place(2.6, alert + [(1.3 + s, w) for s, w in alert])
    urgent = loopable(echo(urgent, 0.18, 0.25, 2), 2.6)
    # low: comm chirp, "bip-bip-boop", twice, with an echo.
    def bip(f, l=0.11):
        return fm(f, l, ratio=3, index=1.0, index_decay=12, decay=10)
    call = [(0, bip(hz(19))), (0.16, bip(hz(19))), (0.32, fm(hz(12), 0.42, ratio=2, index=1.5, decay=3))]
    low = loopable(echo(place(3.0, call + [(1.5 + s, w) for s, w in call]), 0.24, 0.3, 2), 3.0)
    # going low: a power-down sweep with two falling blips.
    sweep = fm(1600, 1.0, ratio=1.01, index=0.4, index_decay=1, decay=1.5, glide_to=400)
    going = echo(place(2.0, [(0, sweep * 0.8), (1.05, bip(hz(10), 0.18)), (1.3, bip(hz(5), 0.22))]), 0.22, 0.3, 3)
    # high: the engine hum, a low FM drone that swells, and a soft console tone on top.
    hum = fm(hz(-26), 2.6, ratio=2.0, index=2.5, index_decay=0.4, decay=0.5, attack=0.6)
    high = place(2.8, [(0, hum), (0.5, fm(hz(4), 0.6, ratio=2, index=0.8, decay=3) * 0.5), (0.9, fm(hz(9), 0.7, ratio=2, index=0.8, decay=3) * 0.5)])
    # no readings: a crackle of static, then a lonely ping and its echo.
    static = noise(0.5, 0.6) * env(0.5, 0.02, 0.2) * 0.35
    sonar = echo(place(2.0, [(0, static), (0.55, fm(hz(14), 0.35, ratio=2.01, index=0.6, decay=6))]), 0.3, 0.45, 4)
    # reminder: data received, three quick rising blips.
    remind = echo(place(1.2, [(0, bip(hz(12))), (0.1, bip(hz(16))), (0.2, bip(hz(19))), (0.32, fm(hz(24), 0.4, ratio=2, index=0.6, decay=4))]), 0.2, 0.3, 2)
    return {'urgent': urgent, 'low': low, 'going_low': going, 'high': high, 'no_readings': sonar, 'reminder': remind}


def glass():
    """Glass: bright and clean. Kalimba plucks and singing-bowl strikes, no space effects."""
    def kal(f, l=0.9):
        return tone(f, l, partials=((1, 1), (2.76, 0.18), (5.4, 0.05)), decay=5, attack=0.002)
    def bowl(f, l=2.6):
        return tone(f, l, partials=((1, 1), (2.71, 0.45), (5.15, 0.2), (8.9, 0.07)), decay=1.4, attack=0.004, vibrato=0.002, vib_rate=4.5)
    urgent = place(2.4, [(rep * 1.2 + i * 0.1, kal(f, 0.6)) for rep in range(2) for i, f in enumerate([hz(19), hz(23), hz(26), hz(31), hz(26)])] + [(0, bowl(hz(14), 2.4) * 0.35)])
    urgent = loopable(urgent, 2.4)
    low = loopable(place(3.0, [(0, kal(hz(16))), (0.22, kal(hz(14))), (0.44, kal(hz(9), 1.2)), (1.5, kal(hz(16))), (1.72, kal(hz(14))), (1.94, kal(hz(9), 1.2))]), 3.0)
    going = tone(hz(14), 2.6, partials=((1, 1), (2.71, 0.4), (5.15, 0.15)), decay=1.3, attack=0.004, glide_to=hz(9))
    high = place(2.0, [(0, kal(hz(-3), 1.4)), (0.28, kal(hz(4), 1.6)), (0, bowl(hz(-15), 2.0) * 0.4)])
    sonar = place(1.6, [(0, bowl(hz(21), 0.9) * 0.8), (0.45, bowl(hz(21), 1.1) * 0.6)])
    remind = place(2.0, [(i * 0.07, kal(f, 1.6)) for i, f in enumerate([hz(15), hz(19), hz(22), hz(27)])])
    return {'urgent': urgent, 'low': low, 'going_low': going, 'high': high, 'no_readings': sonar, 'reminder': remind}


def clear():
    """Clear: plain and functional, after medical alarm patterns (IEC 60601-1-8): high priority is a
    fast ten-pulse burst, medium three pulses, low two. Soft-edged sine pulses so it isn't harsh."""
    def pulse(f, l=0.15):
        return tone(f, l, partials=((1, 1), (2, 0.25), (3, 0.1)), attack=0.012, release=0.03)
    c, e, g, c2 = hz(3), hz(7), hz(10), hz(15)
    ten = []
    at = 0.0
    for i, f in enumerate([c, e, g, g, c2, c, e, g, g, c2]):
        ten.append((at, pulse(f)))
        at += 0.2 if i not in (2, 4, 7) else 0.42
    urgent = loopable(place(3.2, ten), 3.2)
    low = loopable(place(2.6, [(0, pulse(c, 0.2)), (0.28, pulse(e, 0.2)), (0.56, pulse(g, 0.2))]), 2.6)
    going = place(1.4, [(0, pulse(g, 0.22)), (0.32, pulse(e, 0.22)), (0.64, pulse(c, 0.3))])
    high = place(1.2, [(0, pulse(hz(-2), 0.3)), (0.4, pulse(hz(-2), 0.3))])
    sonar = place(1.2, [(0, pulse(hz(5), 0.2)), (0.6, pulse(hz(5), 0.2))])
    remind = place(1.0, [(0, pulse(c2, 0.12)), (0.16, pulse(g, 0.18))])
    return {'urgent': urgent, 'low': low, 'going_low': going, 'high': high, 'no_readings': sonar, 'reminder': remind}


PACKS = {'astral': astral, 'orbit': orbit, 'glass': glass, 'clear': clear}
LEVELS = {'urgent': 0.97, 'low': 0.95, 'going_low': 0.9, 'high': 0.9, 'no_readings': 0.88, 'reminder': 0.85}


def write(path, wave, role):
    wavfile.write(path, RATE, (finish(wave, LEVELS[role]) * 32767).astype(np.int16))


if __name__ == '__main__':
    if sys.argv[1:2] == ['--preview']:
        out = sys.argv[2]
        os.makedirs(out, exist_ok=True)
        for name, make in PACKS.items():
            for role, wave in make().items():
                write(os.path.join(out, f'{name}-{role}.wav'), wave, role)
                print(name, role, f'{len(wave) / RATE:.1f}s')
    else:
        name = sys.argv[1]
        for role, wave in PACKS[name]().items():
            write(os.path.join(RAW, APP_NAMES[role] + '.wav'), wave, role)
            print(name, role, '->', APP_NAMES[role])
