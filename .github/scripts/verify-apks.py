"""Verify native ABI contents and record actual APK sizes, hashes and reduction."""
import hashlib
import json
from pathlib import Path
import zipfile

dist = Path('dist')
sizes = {}
hashes = []
for label, expected in [('arm64', {'arm64-v8a'}),
                        ('universal', {'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'})]:
    apk = dist / f'SyncWatch-1.2.1-{label}.apk'
    with zipfile.ZipFile(apk) as archive:
        abis = {name.split('/')[1] for name in archive.namelist()
                if name.startswith('lib/') and name.endswith('.so')}
        assert abis == expected, (apk.name, abis, expected)
        for abi in abis:
            assert any(name.startswith(f'lib/{abi}/') and 'libvlc' in name for name in archive.namelist()), abi
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    sizes[label] = {'file': apk.name, 'bytes': apk.stat().st_size,
                    'MiB': round(apk.stat().st_size / 1048576, 2),
                    'abis': sorted(abis), 'sha256': digest}
    hashes.append(f'{digest}  {apk.name}')
sizes['reduction_percent'] = round((1 - sizes['arm64']['bytes'] / sizes['universal']['bytes']) * 100, 2)
(dist / 'APK-sizes.json').write_text(json.dumps(sizes, indent=2) + '\n', encoding='utf-8')
(dist / 'SHA256SUMS.txt').write_text('\n'.join(hashes) + '\n', encoding='utf-8')
print(json.dumps(sizes, indent=2))
print('\n'.join(hashes))
