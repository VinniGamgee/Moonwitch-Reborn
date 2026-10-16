#!/usr/bin/env python3
"""Merge one exported build's raw profiles with the matching Android NDK."""
import argparse
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--ndk', type=Path, required=True)
parser.add_argument('--profiles', type=Path, required=True, help='Extracted export directory')
parser.add_argument('--output', type=Path, required=True)
a = parser.parse_args()
revision = (a.ndk / 'source.properties').read_text()
if '28.2.13676358' not in revision:
    parser.error('Use Android NDK 28.2.13676358, matching the training build')
metadata = a.profiles / 'build.txt'
if not metadata.is_file():
    parser.error('Missing build.txt from the exported ZIP')
raw = sorted(a.profiles.glob('*.profraw'))
if not raw:
    parser.error('No .profraw profiles found')
tools = list((a.ndk / 'toolchains/llvm/prebuilt').glob('*/bin/llvm-profdata'))
if len(tools) != 1:
    parser.error('Expected one llvm-profdata tool in this NDK')
a.output.parent.mkdir(parents=True, exist_ok=True)
subprocess.run([str(tools[0]), 'merge', '-o', str(a.output), *map(str, raw)], check=True)
subprocess.run([str(tools[0]), 'show', str(a.output)], check=True)
a.output.with_suffix('.build.txt').write_text(metadata.read_text())
print(f'Created {a.output}; retain the accompanying build metadata')
