"""
Sukoon's own alarm sounds, synthesized: bell and wood tones with soft attacks and natural decays,
nothing square or buzzy. Urgent stays unmistakable: higher, brighter, faster, and it loops.
Writes 16-bit mono WAVs to res/raw (Android plays them through MediaPlayer, looping where asked).
"""
import os
import numpy as np
from scipy.io import wavfile

RATE = 32000
OUT = r'D:\Coding\sukoon\app\src\main\res\raw'
os.makedirs(OUT, exist_ok=True)


def note(freq, length, partials=((1, 1.0), (2.0, 0.42), (3.01, 0.18), (4.2, 0.08)), decay=3.2, attack=0.006, glide_to=None):
    """A struck tone: a fundamental and inharmonic-ish partials (bell-like), each decaying a little faster than the last."""
    t = np.arange(int(RATE * length)) / RATE
    f = freq if glide_to is None else freq * (glide_to / freq) ** (t / length)
    phase = 2 * np.pi * np.cumsum(f) / RATE
    wave = np.zeros_like(t)
    for ratio, amp in partials:
        wave += amp * np.sin(phase * ratio) * np.exp(-t * decay * (0.7 + ratio * 0.3))
    env = np.minimum(1, t / attack)                       # click-free start
    env *= np.minimum(1, (length - t) / 0.03).clip(0, 1)  # click-free end
    return wave * env


def wood(freq, length=0.25):
    """A soft knock: short, low, damped quickly (marimba / wood block)."""
    return note(freq, length, partials=((1, 1.0), (3.9, 0.25), (9.2, 0.05)), decay=14, attack=0.002)


def place(total, pieces):
    out = np.zeros(int(RATE * total))
    for start, wave in pieces:
        i = int(RATE * start)
        out[i:i + len(wave)] += wave[:len(out) - i]
    return out


def save(name, wave, level):
    peak = np.max(np.abs(wave)) or 1
    data = (wave / peak * level * 32767).astype(np.int16)
    wavfile.write(os.path.join(OUT, name + '.wav'), RATE, data)
    print(name, f'{len(wave) / RATE:.2f}s', os.path.getsize(os.path.join(OUT, name + '.wav')) // 1024, 'KB')


A4 = 440.0
def hz(semitones_from_a4):
    return A4 * 2 ** (semitones_from_a4 / 12)

# Rise (urgent low): E6 G6 B6 quick and bright, twice per loop, then a short breath. Loops for a minute.
rise_notes = [hz(19), hz(22), hz(26)]
rise = place(2.2, [(i * 0.13 + rep * 0.9, note(f, 0.55, decay=5.5)) for rep in range(2) for i, f in enumerate(rise_notes)])
save('sukoon_rise', rise, 0.95)

# Ripple (low): A5 then E5, a falling fourth, twice. Clear and calm, but plainly asking.
ripple = place(2.6, [(0.0, note(hz(12), 1.0, decay=3.0)), (0.32, note(hz(7), 1.1, decay=2.6)),
                     (1.3, note(hz(12), 1.0, decay=3.0)), (1.62, note(hz(7), 1.0, decay=2.6))])
save('sukoon_ripple', ripple, 0.9)

# Drift (going low): one soft tone gliding down a fifth, a heads-up rather than an alarm.
drift = place(1.8, [(0.0, note(hz(10), 1.6, decay=2.2, glide_to=hz(3), partials=((1, 1.0), (2.0, 0.3), (3.0, 0.1))))])
save('sukoon_drift', drift, 0.8)

# Warm (high): two low wooden notes, D4 and A4. Noticeable, never startling.
warm = place(1.4, [(0.0, wood(hz(-7), 0.6)), (0.22, wood(hz(0), 0.7)), (0.0, note(hz(-7), 1.2, decay=3.5, partials=((1, 0.35),)))])
save('sukoon_warm', warm, 0.85)

# Knock (no readings): two soft knocks, like someone checking in.
knock = place(0.9, [(0.0, wood(hz(-2), 0.3)), (0.24, wood(hz(-2), 0.3))])
save('sukoon_knock', knock, 0.8)

# Chime (reminders): C6 E6 G6 rolled, a small bright chord that rings out.
chime = place(1.9, [(0.0, note(hz(15), 1.6, decay=2.4)), (0.09, note(hz(19), 1.5, decay=2.4)), (0.18, note(hz(22), 1.5, decay=2.4))])
save('sukoon_chime', chime, 0.8)
