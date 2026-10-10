"""Expose deterministic emulator screenshots in CI logs for visual review, as well as artifacts.

Only synthetic media and the test's Alex/Movie night identity appear in these images.
Chunking keeps each log line short enough for the GitHub log viewer.
"""
import base64
import hashlib
from pathlib import Path

for image in sorted(Path('app/build/reports/ui').glob('*.png')):
    data = image.read_bytes()
    encoded = base64.b64encode(data).decode('ascii')
    chunks = [encoded[start:start + 3000] for start in range(0, len(encoded), 3000)]
    print(f'UI_META {image.name} {len(chunks)} {hashlib.sha256(data).hexdigest()}')
    for index, chunk in enumerate(chunks):
        print(f'UI_DATA {image.name} {index} {chunk}')
