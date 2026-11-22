"""Optional media check using PyAV, NumPy and the compiled Java regression's classpath."""
import argparse
import base64
from fractions import Fraction
from pathlib import Path
import subprocess
import tempfile

import av
import numpy as np


RATE = 48000
FPS = 30
USEC = Fraction(1, 1_000_000)
OFFSET = 200_000
FLASHES = (0.5, 1.5, 2.5)


def encode_inputs(folder):
    with av.open(str(folder / "video.mp4"), "w") as container:
        stream = container.add_stream("libx264", rate=FPS)
        stream.width, stream.height, stream.pix_fmt = 320, 180, "yuv420p"
        stream.codec_context.max_b_frames = 0
        for index in range(3 * FPS):
            pixels = np.empty((180, 320, 3), dtype=np.uint8)
            pixels[:] = (22, 53, 68)
            x = index * 3
            pixels[:, x:x+7] = (50, 205, 180)
            if any(0 <= index / FPS - start < 0.1 for start in FLASHES):
                pixels[:] = 245
            frame = av.VideoFrame.from_ndarray(pixels, format="rgb24")
            frame.pts, frame.time_base = index, Fraction(1, FPS)
            container.mux(stream.encode(frame))
        container.mux(stream.encode())
    with av.open(str(folder / "audio.m4a"), "w") as container:
        stream = container.add_stream("aac", rate=RATE)
        stream.layout = "stereo"
        stream.bit_rate = 128000
        # Deliberately continue after the last video frame to exercise trimming.
        for index in range(0, 160 * 1024, 1024):
            clock = np.arange(index, index+1024) / RATE
            time = clock + OFFSET / 1_000_000
            on = np.zeros(len(clock), dtype=bool)
            for start in FLASHES:
                on |= (time >= start) & (time < start + .1)
            samples = (.45 * np.sin(2 * np.pi * 880 * clock) * on).astype(np.float32)
            frame = av.AudioFrame.from_ndarray(np.stack((samples, samples)), format="fltp", layout="stereo")
            frame.pts, frame.time_base, frame.sample_rate = index, Fraction(1, RATE), RATE
            container.mux(stream.encode(frame))
        container.mux(stream.encode())


def us(ticks, base):
    return round(ticks * base / USEC)


def fixture(source, target, mime, pts_origin):
    with av.open(str(source)) as media, target.open("w", encoding="ascii") as out:
        stream = media.streams[0]
        duration = us(stream.duration, stream.time_base)
        print(mime, duration, FPS if mime.startswith("video/") else 0, sep="\t", file=out)
        for d in media.demux(stream):
            if d.pts is None or d.pts < 0:
                continue
            print(pts_origin + us(d.pts, d.time_base), int(d.is_keyframe),
                  base64.b64encode(bytes(d)).decode("ascii"), sep="\t", file=out)


def remux(folder, classpath, result):
    video, audio = folder / "v.tsv", folder / "a.tsv"
    # Distinct source clock origins force the app to align to its recording epoch.
    fixture(folder / "video.mp4", video, "video/avc", 7000000)
    fixture(folder / "audio.m4a", audio, "audio/mp4a-latm", 9000000)
    rows = subprocess.check_output(
        ["java", "-cp", classpath, "io.github.phiscript.record.GameAudioMuxerRegression",
         str(video), str(audio), str(OFFSET)], encoding="ascii")
    with av.open(str(folder / "video.mp4")) as v, av.open(str(folder / "audio.m4a")) as a, av.open(str(result), "w") as dest:
        streams = {"video/avc": dest.add_stream_from_template(v.streams.video[0]),
                   "audio/mp4a-latm": dest.add_stream_from_template(a.streams.audio[0])}
        packets = []
        for row in rows.splitlines():
            mime, pts, flags, encoded = row.split("\t")
            packet = av.Packet(base64.b64decode(encoded))
            packet.stream = streams[mime]
            packet.time_base = USEC
            packet.pts = packet.dts = int(pts)
            packet.duration = us(1, Fraction(1, FPS)) if mime == "video/avc" else us(1024, Fraction(1, RATE))
            packet.is_keyframe = int(flags) == 1
            packets.append(packet)
        for packet in sorted(packets, key=lambda item: item.pts):
            dest.mux(packet)


def verify(path):
    with av.open(str(path)) as media:
        v, a = media.streams.video[0], media.streams.audio[0]
        assert v.codec_context.name == "h264" and a.codec_context.name == "aac"
        assert a.codec_context.sample_rate == RATE and len(a.codec_context.layout.channels) == 2
        assert abs(float(v.duration * v.time_base) - 3) <= 1 / FPS
        assert abs(float((a.start_time + a.duration) * a.time_base) - 3) <= 1024 / RATE + .003
        light = []
        frames = list(media.decode(v))
        assert len(frames) == 90
        previous = False
        for f in frames:
            on = f.to_ndarray(format="rgb24").mean() > 220
            if on and not previous:
                light.append(float(f.pts * f.time_base))
            previous = on
    beeps = []
    previous = False
    with av.open(str(path)) as media:
        for f in media.decode(audio=0):
            samples = f.to_ndarray()[0]
            for offset in range(0, len(samples), 128):
                segment = samples[offset:offset+128]
                on = np.sqrt(np.mean(segment ** 2)) > .14
                if on and not previous:
                    beeps.append(float(f.pts * f.time_base) + offset / RATE)
                previous = on
    assert len(light) == len(beeps) == len(FLASHES), (light, beeps)
    errors = [round(1000 * (beep - flash), 1) for flash, beep in zip(light, beeps)]
    assert max(abs(err) for err in errors) <= 1000 / FPS, errors
    assert max(errors) - min(errors) < 4, errors
    print(f"PASS decoded {len(frames)} AVC frames, stereo 48 kHz AAC, 3 beeps, 200 ms capture offset; "
          f"flash-to-beep errors (ms) {errors}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("classpath")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory() as temp:
        folder = Path(temp)
        encode_inputs(folder)
        remux(folder, args.classpath, args.output)
    verify(args.output)
