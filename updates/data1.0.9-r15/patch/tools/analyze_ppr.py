#!/usr/bin/env python3
"""Read-only, bounded Phira Pro v5 JSON/Zstandard replay statistics.

Requires Python 3.10+ and `pip install zstandard`. The source replay is never
rewritten. Output omits player identifiers and local chart paths. Optional PGR
chart matching follows Phira-Pro 3776f5f's parse/pgr.rs and core/line.rs.
Usage: python analyze_ppr.py INPUT.ppr --chart AT.json --out OUTPUT_DIRECTORY
"""
import argparse
import bisect
import collections
import hashlib
import io
import json
import math
from pathlib import Path
import struct

MAX_BYTES = 128 << 20
MAX_EVENTS = 2_000_000
KINDS = ['Perfect', 'Good', 'Bad', 'Miss', 'PerfectPlus', 'HoldPerfect',
         'HoldGood', 'Great', 'Ok', 'Meh']
TYPES = {1: 'Tap', 2: 'Drag', 3: 'Hold', 4: 'Flick'}


def bounded_read(path):
    with Path(path).open('rb') as stream:
        data = stream.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError('Input exceeds 128 MiB')
    return data


def load_ppr(path):
    import zstandard
    data = bounded_read(path)
    if len(data) < 12 or data[:8] != b'PHIRAREC':
        raise ValueError('Invalid PPR header')
    version, = struct.unpack_from('<I', data, 8)
    if version != 5:
        raise ValueError('This analyzer supports v5; v6 requires capture input reconstruction')
    with zstandard.ZstdDecompressor().stream_reader(io.BytesIO(data[12:])) as stream:
        payload = stream.read(MAX_BYTES + 1)
    if len(payload) > MAX_BYTES:
        raise ValueError('Decompressed JSON exceeds 128 MiB')
    obj = json.loads(payload, parse_constant=lambda v: (_ for _ in ()).throw(ValueError(v)))
    for key in ('frames', 'touches', 'judges'):
        events = obj.get(key, [])
        if not isinstance(events, list) or len(events) > MAX_EVENTS:
            raise ValueError('Invalid or excessive ' + key)
    for event in obj['touches']:
        if event['phase'] not in (0, 1, 2) or not all(math.isfinite(event[k]) for k in ('t', 'x', 'y')):
            raise ValueError('Invalid touch event')
    for event in obj['judges']:
        if event['kind'] not in range(10) or not all(math.isfinite(event[k]) for k in ('t', 'diff')):
            raise ValueError('Invalid judge event')
    if not all(math.isfinite(t) for t in obj['frames']):
        raise ValueError('Invalid frame timestamp')
    order = {key: all(a <= b for a, b in zip(
        obj[key] if key == 'frames' else [e['t'] for e in obj[key]],
        (obj[key] if key == 'frames' else [e['t'] for e in obj[key]])[1:]))
        for key in ('frames', 'touches', 'judges')}
    # Stable in-memory sorting preserves same-time source event order.
    obj['frames'].sort()
    obj['touches'].sort(key=lambda e: e['t'])
    obj['judges'].sort(key=lambda e: e['t'])
    return obj, {'version': version, 'bytes': len(data), 'json_bytes': len(payload),
                 'sha256': hashlib.sha256(data).hexdigest(), 'source_ordered': order}


def quantiles(values):
    values = sorted(values)
    if not values:
        return {'n': 0}
    out = {'n': len(values), 'mean': sum(values) / len(values)}
    for p, key in [(0, 'min'), (.01, 'p01'), (.05, 'p05'), (.1, 'p10'),
                   (.25, 'p25'), (.5, 'median'), (.75, 'p75'), (.9, 'p90'),
                   (.95, 'p95'), (.99, 'p99'), (1, 'max')]:
        pos = p * (len(values) - 1)
        i = int(pos)
        out[key] = values[i] + (values[min(i + 1, len(values) - 1)] - values[i]) * (pos - i)
    return out


def f32(value):
    return struct.unpack('<f', struct.pack('<f', value))[0]


def pgr_notes(chart):
    """Native height integration and stable cache order; do not trust floorPosition."""
    lines = chart['judgeLineList']
    max_time = max((n['time'] + (n['holdTime'] if n['type'] == 3 else 0))
                   * 1.875 / line['bpm'] for line in lines
                   for n in line.get('notesAbove', []) + line.get('notesBelow', [])) + 1
    result = []
    for line in lines:
        ratio = 1.875 / line['bpm']
        events = [dict(e) for e in line['speedEvents'] if e['startTime'] <= e['endTime']]
        if not events:
            raise ValueError('PGR line has no valid speed events')
        events[0]['startTime'] = 0
        keys = []
        pos = 0.0
        for event in events[:-1]:
            keys.append((event['startTime'] * ratio, pos))
            pos = f32(pos + f32(f32((event['endTime'] - event['startTime']) * ratio) * f32(event['value'])))
        last = events[-1]
        keys.append((last['startTime'] * ratio, pos))
        keys.append((max_time, f32(pos + f32(f32(max_time - last['startTime'] * ratio) * f32(last['value'])))))
        keys = [(t, f32(v / f32(.83175))) for t, v in keys]
        times = [t for t, _ in keys]
        def height(t):
            i = max(0, bisect.bisect_right(times, t) - 1)
            if i + 1 >= len(keys) or t < times[0]:
                return keys[i][1]
            u = f32((t - times[i]) / (times[i + 1] - times[i]))
            return f32(keys[i][1] + f32(f32(keys[i + 1][1] - keys[i][1]) * u))
        notes = []
        for name, above in [('notesAbove', True), ('notesBelow', False)]:
            for n in sorted(line.get(name, []), key=lambda x: x['time']):
                sec = n['time'] * ratio
                speed = 1.0 if n['type'] == 3 else f32(n['speed'])
                notes.append(dict(n, sec=sec, end_sec=sec + n.get('holdTime', 0) * ratio,
                                  above=above, realSpeed=speed, native_height=height(sec)))
        notes.sort(key=lambda n: (n['type'] != 3, not n['above'], n['realSpeed'], n['native_height'] * n['realSpeed']))
        result.append(notes)
    return result


def analyze(replay, container, chart_bytes=None, extra_bytes=None, info_offset=0):
    meta = replay.get('meta', {})
    chart_ref = replay.get('chart') or {}
    summary = {'container': container, 'chart': {
        'name': meta.get('name'), 'level': meta.get('level'), 'difficulty': meta.get('difficulty'),
        'fingerprint': meta.get('fingerprint'), 'audio_fingerprint': meta.get('audio_fingerprint'),
        'reference_kind': next(iter(chart_ref), None), 'embedded_chart': False,
        'recorded_chart_url_present': bool(replay.get('chart_file'))},
        'result': {k:v for k,v in (meta.get('result') or {}).items() if k in
                   ('score','accuracy','maxCombo','numOfNotes','counts','gradeCounts','grading','std')},
        'offset_seconds': replay.get('offset'),
        'speed': replay.get('speed'), 'mods': replay.get('mods'),
        'aspect_ratio': replay.get('aspect_ratio'), 'settings': replay.get('settings'),
        'gameplay': replay.get('gameplay'), 'grading': replay.get('grading')}
    touches = replay['touches']
    active, contacts, anomalies = {}, [], []
    peak = 0
    for e in touches:
        ident = e['id']
        if e['phase'] == 0:
            if ident in active:
                anomalies.append({'t': e['t'], 'kind': 'duplicate_down', 'id': ident})
            active[ident] = dict(id=ident, start=e['t'], x=e['x'], y=e['y'], moves=0)
        elif ident not in active:
            anomalies.append({'t': e['t'], 'kind': 'orphan_event', 'id': ident})
        elif e['phase'] == 1:
            active[ident]['moves'] += 1
        else:
            c = active.pop(ident)
            contacts.append(dict(c, end=e['t'], end_x=e['x'], end_y=e['y'], duration_ms=(e['t']-c['start'])*1000))
        peak = max(peak, len(active))
    durations = [c['duration_ms'] for c in contacts]
    summary['touches'] = dict(events=len(touches), phases=dict(collections.Counter(e['phase'] for e in touches)),
        contact_count=len(contacts), peak_concurrent=peak, open_contacts=len(active), anomalies=anomalies,
        duration_ms=quantiles(durations),
        durations_strictly_under_ms={str(n): sum(d < n for d in durations) for n in (1, 4, 8, 12, 16, 24, 32, 48)},
        very_short_contacts=[c for c in contacts if c['duration_ms'] < 8])
    summary['frames'] = dict(count=len(replay['frames']), interval_ms=quantiles([(b-a)*1000 for a,b in zip(replay['frames'],replay['frames'][1:])]))
    summary['judgements'] = dict(events=len(replay['judges']), kinds=dict(collections.Counter(KINDS[e['kind']] for e in replay['judges'])),
        final_unique_notes=len(set((e['line'],e['note']) for e in replay['judges'] if e['kind'] not in (5,6))))
    mapped = []
    if chart_bytes is not None:
        digest = hashlib.sha256(b'PhiraReplayChart1\0' + struct.pack('<Q', len(chart_bytes)) + chart_bytes)
        if extra_bytes is not None:
            digest.update(struct.pack('<Q', len(extra_bytes)) + extra_bytes)
        digest.update(struct.pack('<f', info_offset))
        fingerprint = digest.hexdigest()
        summary['chart'].update(candidate_fingerprint=fingerprint, exact_fingerprint_match=fingerprint == meta.get('fingerprint'))
        chart = json.loads(chart_bytes)
        ns = pgr_notes(chart)
        summary['chart'].update(lines=len(ns), notes=sum(map(len,ns)), noise_areas=len(chart.get('blockAreaList', chart.get('BlockAreaList', []))))
        for event in replay['judges']:
            n = ns[event['line']][event['note']]
            mapped.append(dict(event, note_type=TYPES[n['type']], note_time=n['sec'], note_end=n['end_sec']))
        per_type = {}
        for typ in TYPES.values():
            finals = [e for e in mapped if e['note_type'] == typ and e['kind'] not in (5,6)]
            per_type[typ] = dict(final_count=len(finals), kinds=dict(collections.Counter(KINDS[e['kind']] for e in finals)))
        tap_diffs = [e['diff']*1000 for e in mapped if e['note_type']=='Tap' and e['kind'] != 3]
        heads = [e['diff']*1000 for e in mapped if e['kind'] in (5,6)]
        misses = [e for e in mapped if e['kind'] == 3]
        nonhold_miss_delay = [(e['t']-e['note_time'])*1000 for e in misses if e['note_type'] != 'Hold']
        summary['judgements'].update(by_note_type=per_type, tap_hit_error_ms=quantiles(tap_diffs),
            hold_head_error_ms=quantiles(heads), tap_plus_hold_head_error_ms=quantiles(tap_diffs+heads),
            nonhold_miss_delay_ms=quantiles(nonhold_miss_delay),
            all_head_events_map_to_hold=all(e['note_type']=='Hold' for e in mapped if e['kind'] in (5,6)),
            misses_by_10s={str(s):sum(s<=e['note_time']<s+10 for e in misses) for s in range(0,151,10)})
    summary['limitations'] = [
        'v5 does not include hardware timestamps, actual device DPI, screen size, app build, or internal blocked-finger state.',
        'Stationary touches are omitted; hold the last position until the next explicit event. A MOVE gap does not prove no active input.',
        'Same-frame DOWN/UP durations are frame-quantized; zero does not prove zero hardware duration.',
        'Judge events do not identify fingers. Temporal adjacency alone cannot assign an individual short contact as the cause of a miss.',
        'Drag/flick diffs are synthetic zero; hold final diffs repeat the head. Timing statistics use Tap hits and Hold head events once.',
        'Active judgeAlgorithm determines settings; stored inactive Phigros DPI/sample settings do not establish physical device characteristics.']
    return summary, contacts, mapped


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('replay', type=Path)
    parser.add_argument('--chart', type=Path)
    parser.add_argument('--extra', type=Path)
    parser.add_argument('--info-offset', type=float, default=0)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    replay, container = load_ppr(args.replay)
    summary, contacts, mapped = analyze(replay, container,
        bounded_read(args.chart) if args.chart else None,
        bounded_read(args.extra) if args.extra else None, args.info_offset)
    args.out.mkdir(parents=True, exist_ok=True)
    for name, obj in [('ppr-statistics.json',summary),('contacts.json',contacts),('mapped-judgements.json',mapped)]:
        (args.out/name).write_text(json.dumps(obj,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(dict(version=container['version'],contacts=len(contacts),judgements=len(mapped),
                         exact_chart=summary['chart'].get('exact_fingerprint_match')),ensure_ascii=False))


if __name__ == '__main__':
    main()
