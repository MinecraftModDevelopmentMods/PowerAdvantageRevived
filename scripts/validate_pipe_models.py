#!/usr/bin/env python3
"""Validate all 64 pipe variants, model references and rotated connection directions."""
from pathlib import Path
import itertools
import json
import math

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/poweradvantage'
DIRECTIONS = {'down': (0,-1,0), 'east': (1,0,0), 'north': (0,0,-1),
              'south': (0,0,1), 'up': (0,1,0), 'west': (-1,0,0)}

def read(path):
    def unique(pairs):
        result = {}
        for key,value in pairs:
            assert key not in result, f'Duplicate JSON key {key} in {path}'
            result[key] = value
        return result
    return json.loads(path.read_text(), object_pairs_hook=unique)

def geometry(model):
    if ':' not in model:
        model = 'minecraft:' + model
    namespace,path = model.split(':',1)
    if namespace != 'poweradvantage':
        return []
    obj = read(ROOT / 'models' / (path + '.json'))
    for texture in obj.get('textures',{}).values():
        if texture.startswith('poweradvantage:'):
            assert texture.split(':',1)[1].startswith(('block/', 'item/')), f'Outside default block atlas directories: {texture}'
            assert (ROOT / 'textures' / (texture.split(':',1)[1] + '.png')).is_file(), texture
    return obj.get('elements') or geometry(obj['parent'])

def rotate(vector, x, y):
    a,b,c = vector
    x,y = math.radians(-x),math.radians(-y)
    b,c = b*math.cos(x)-c*math.sin(x), b*math.sin(x)+c*math.cos(x)
    a,c = a*math.cos(y)+c*math.sin(y), -a*math.sin(y)+c*math.cos(y)
    return tuple(round(v) for v in (a,b,c))

for block in ('fluid_pipe','fluid_pipe_terminal'):
    states = read(ROOT / 'blockstates' / (block + '.json'))['variants']
    assert len(states) == 64, block
    for key,variant in states.items():
        flags = dict(part.split('=') for part in key.split(','))
        assert set(flags) == set(DIRECTIONS), key
        expected = {DIRECTIONS[d] for d,value in flags.items() if value == 'true'}
        elements = geometry(variant['model'])
        actual = set()
        for element in elements:
            for direction,vector in DIRECTIONS.items():
                axis = next(i for i,v in enumerate(vector) if v)
                bound = element['to'][axis] if vector[axis] > 0 else element['from'][axis]
                if bound == (16 if vector[axis] > 0 else 0):
                    actual.add(rotate(vector,variant.get('x',0),variant.get('y',0)))
        assert actual == expected, f'{block}: {key}: expected {expected}, got {actual}'
print('PASS: 128 variants, rotated ports, model parents, textures and duplicate keys')
