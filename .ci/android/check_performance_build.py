#!/usr/bin/env python3
"""Verify effective native compiler flags, not only Gradle's requested options."""
import json
from pathlib import Path
import sys

mode = sys.argv[1]
commands = []
for database in Path('src/android/app/.cxx').rglob('compile_commands.json'):
    commands.extend(json.loads(database.read_text()))
assert commands, 'No native compilation database found'
for suffix in ('/src/core/memory.cpp', '/src/video_core/renderer_vulkan/vk_pipeline_cache.cpp',
               '/src/android/app/src/main/jni/native_pgo.cpp'):
    matching = [c for c in commands if c['file'].endswith(suffix)]
    assert matching, f'Missing compilation unit {suffix}'
    for entry in matching:
        flags = entry.get('command') or ' '.join(entry['arguments'])
        assert '-flto=thin' in flags, f'ThinLTO missing for {suffix}'
        if mode == 'GENERATE':
            assert '-fprofile-generate' in flags and '-fprofile-update=atomic' in flags
        elif mode == 'USE':
            assert '-fprofile-use=' in flags
        else:
            assert '-fprofile-generate' not in flags and '-fprofile-use=' not in flags
print(f'Effective ThinLTO and PGO {mode} flags verified on core, Vulkan and JNI')
