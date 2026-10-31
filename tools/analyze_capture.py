"""Summarize a Teyes CAN Probe JSONL without guessing RPM or speed units."""
import argparse
import collections
import json
from pathlib import Path


def profile_parts(value):
    return {'value': value, 'protocol': value & 65535, 'variant': (value >> 16) & 65535}


def psa_temperature(raw, offset):
    # Air_0252_WC2_PSAALL.java:99-118. Offset is DATA[242]; no assumed zero.
    if raw in (-1, -2, -3):
        return {-1: 'NO', -2: 'LOW', -3: 'HI'}[raw]
    return None if offset is None else (raw + offset * 10) / 10.0


def mcu_payload(raw):
    if not isinstance(raw,list) or not all(type(v) is int for v in raw):
        return {'kind':'invalid','unsigned':None}
    if not raw:
        return {'kind':'empty','unsigned':[]}
    if raw==[0]:
        return {'kind':'snapshot_or_zero_payload','unsigned':[0]}
    return {'kind':'payload_candidate','unsigned':[v & 255 for v in raw]}


def summarize(lines):
    counts = collections.Counter()
    all_counts = collections.Counter()
    raw_counts = collections.Counter()
    key_events = 0
    latest = {}
    profiles = {}
    rejected = 0
    summary = None
    for number, line in enumerate(lines, 1):
        if not line.strip():
            continue
        try:
            item = json.loads(line)
        except ValueError as e:
            raise ValueError(f'Invalid JSON at line {number}') from e
        if item.get('type') == 'capture_summary':
            summary = item
        if item.get('type') == 'android_key' and item.get('synthetic') is False:
            key_events += 1
        if item.get('type') != 'update':
            continue
        module=item.get('module')
        if item.get('synthetic') is not False or type(module) is not int or module not in (0,7,10):
            rejected += 1
            continue
        code = item.get('code')
        if type(code) is not int or not 0 <= code < 1200:
            raise ValueError(f'Invalid update code at line {number}')
        profile = item.get('profile')
        if type(profile) is int:
            profiles[profile] = profile_parts(profile)
        if module==7:
            counts[code] += 1
        all_counts[f'{module}:{code}'] += 1
        if module==7 and code==1019:
            raw_counts[mcu_payload(item.get('ints'))['kind']] += 1
        # Keep values separated by full profile: never merge data from different protocols.
        latest[(profile, module, code)] = item.get('ints')
    temperatures = []
    for profile in profiles:
        if (profile & 65535) != 252:
            continue
        offset_array = latest.get((profile, 7, 242))
        offset = offset_array[0] if offset_array else None
        for code, side in [(27, 'left'), (28, 'right')]:
            values = latest.get((profile, 7, code))
            if values:
                temperatures.append({'profile': profile, 'side': side, 'raw': values[0],
                    'offset242': offset, 'oem_display_value': psa_temperature(values[0], offset),
                    'unit': 'not independently validated'})
    return {'accepted_callbacks': sum(all_counts.values()), 'rejected_non_probe_events': rejected,
            'profiles': list(profiles.values()), 'counts_by_code': dict(sorted(counts.items())),
            'counts_by_module_and_code':dict(sorted(all_counts.items())),
            'mcu_payload_records':dict(raw_counts), 'android_key_events':key_events,
            'psa252_temperature_display': temperatures, 'capture_summary': summary,
            'note': 'Callbacks may be cached. MCU payloads are not proven physical CAN frames. GPS is not CAN speed; 1031/1032 support and units remain unvalidated.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    args = parser.parse_args()
    with args.capture.open(encoding='utf-8-sig') as stream:
        print(json.dumps(summarize(stream), ensure_ascii=False, indent=2))
