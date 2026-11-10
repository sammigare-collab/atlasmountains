#!/usr/bin/env python3
"""Extracts the vanilla worldgen data that tools/build_datapack.py reads.

The generator reads vanilla's own JSON (density functions, noises, biomes, material rules, the
world presets, dimension types) from tools/vanilla-<version>/. That data is Mojang's, so it is not
committed; this script copies it out of the Minecraft jar instead.

    python3 tools/extract_vanilla.py [path/to/minecraft.jar]

With no argument it uses the jar Fabric Loom caches after any Gradle build
(~/.gradle/caches/fabric-loom/<version>/minecraft-merged.jar).
"""
import glob
import os
import shutil
import sys
import zipfile

VERSION = '26.3'
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'tools', f'vanilla-{VERSION}')
PREFIXES = ('data/minecraft/worldgen/', 'data/minecraft/dimension_type/', 'data/minecraft/tags/worldgen/')


def find_jar():
    if len(sys.argv) > 1:
        return sys.argv[1]
    home = os.environ.get('GRADLE_USER_HOME', os.path.join(os.path.expanduser('~'), '.gradle'))
    hits = sorted(glob.glob(os.path.join(home, 'caches', 'fabric-loom', VERSION, 'minecraft-merged*.jar')))
    if not hits:
        sys.exit(f'No Minecraft {VERSION} jar found. Run ./gradlew build once (Loom downloads it), or pass the jar path.')
    return hits[0]


def main():
    jar = find_jar()
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    n = 0
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if name.endswith('/') or not name.startswith(PREFIXES):
                continue
            dest = os.path.join(OUT, name[len('data/minecraft/'):])
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with z.open(name) as src, open(dest, 'wb') as dst:
                shutil.copyfileobj(src, dst)
            n += 1
    print(f'extracted {n} files from {os.path.basename(jar)} into {os.path.relpath(OUT, ROOT)}')


if __name__ == '__main__':
    main()
