#!/usr/bin/env python3
"""Generate isolated node-rule regression tables and an independent load/unload oracle."""

from pathlib import Path

DIRECTORY = Path(__file__).resolve().parent
NODES = (1020201, 1020303)
VEHICLES = tuple((mode, means) for mode, count in ((1, 3), (2, 7), (3, 2))
                 for means in range(1, count + 1))
MOVEMENTS = tuple((0, 0, *vehicle) for vehicle in VEHICLES) + tuple(
    (*vehicle, 0, 0) for vehicle in VEHICLES)
LOAD_ROAD = {(0, 0, 1, means) for means in range(1, 4)}
UNLOAD_ROAD = {(1, means, 0, 0) for means in range(1, 4)}
LOAD_ALL = set(MOVEMENTS[:len(VEHICLES)])
UNLOAD_ALL = set(MOVEMENTS[len(VEHICLES):])
ALL = set(MOVEMENTS)


def generate():
    rules, cases, expected = [], [], []

    def add(node, scenario, group, movement, symmetric=0, include=False):
        rules.append((node if include else -node, scenario, group, *movement, symmetric))

    # Deliberately different defaults expose node isolation and scenario fallback.
    add(NODES[0], -1, -1, (0, 0, 1, -1))
    add(NODES[1], -1, -1, (0, 0, 1, -1), 1, True)
    defaults = {NODES[0]: ALL - LOAD_ROAD, NODES[1]: LOAD_ROAD | UNLOAD_ROAD}

    def record(scenario, title, allowed):
        cases.append((scenario, title))
        for node in NODES:
            for group in (0, 1):
                permitted = allowed[node, group]
                for movement in MOVEMENTS:
                    expected.append((node, scenario, group, *movement,
                                     int(movement not in permitted)))

    record(900, 'Generic scenario and group fallback',
           {(n, g): defaults[n] for n in NODES for g in (0, 1)})
    add(NODES[0], 901, 0, (0, 0, 3, 1), include=True)
    record(901, 'Scenario override is per node; missing group has no rules', {
        (NODES[0], 0): {(0, 0, 3, 1)}, (NODES[0], 1): ALL,
        (NODES[1], 0): defaults[NODES[1]], (NODES[1], 1): defaults[NODES[1]],
    })

    def pair(scenario, title, patterns, selected, group=-1):
        # First node excludes the selection; second node includes only that selection.
        for node in NODES:
            for movement, symmetry in patterns:
                add(node, scenario, group, movement, symmetry, node == NODES[1])
        record(scenario, title, {
            (n, g): (ALL if group != -1 and group != g
                     else selected if n == NODES[1] else ALL - selected)
            for n in NODES for g in (0, 1)
        })

    pair(902, 'Nonmatching transfer rules: exclusions allow; inclusions reject',
         [((1, 1, 2, 1), 1)], set())
    pair(903, 'Exact loading, one direction', [((0, 0, 1, 1), 0)], {(0, 0, 1, 1)})
    pair(904, 'Exact unloading, one direction', [((1, 1, 0, 0), 0)], {(1, 1, 0, 0)})
    pair(905, 'Exact loading and unloading via symmetry', [((0, 0, 1, 1), 1)],
         {(0, 0, 1, 1), (1, 1, 0, 0)})
    pair(906, 'All means of one mode, loading', [((0, 0, 1, -1), 0)], LOAD_ROAD)
    pair(907, 'All means of one mode, unloading', [((1, -1, 0, 0), 0)], UNLOAD_ROAD)
    pair(908, 'All means of one mode, symmetric', [((0, 0, 1, -1), 1)],
         LOAD_ROAD | UNLOAD_ROAD)
    pair(909, 'All modes and means, loading', [((0, 0, -1, -1), 0)], LOAD_ALL)
    pair(910, 'All modes and means, unloading', [((-1, -1, 0, 0), 0)], UNLOAD_ALL)
    pair(911, 'All modes and means, symmetric', [((0, 0, -1, -1), 1)], ALL)
    pair(912, 'Wildcard origin endpoint to exact vehicle', [((-1, -1, 1, 1), 0)],
         {(0, 0, 1, 1)})
    pair(913, 'Exact vehicle to wildcard destination endpoint', [((1, 1, -1, -1), 0)],
         {(1, 1, 0, 0)})
    pair(914, 'Wildcards at both transfer means; no centroid transfer links',
         [((1, -1, 2, -1), 1)], set())
    pair(915, 'Two loading rules form a union',
         [((0, 0, 1, 1), 0), ((0, 0, 2, 1), 0)], {(0, 0, 1, 1), (0, 0, 2, 1)})
    pair(916, 'Two unloading rules form a union',
         [((1, 1, 0, 0), 0), ((2, 1, 0, 0), 0)], {(1, 1, 0, 0), (2, 1, 0, 0)})
    pair(917, 'Loading and unloading rules with different vehicles',
         [((0, 0, 1, 1), 0), ((3, 2, 0, 0), 0)], {(0, 0, 1, 1), (3, 2, 0, 0)})
    pair(918, 'Overlapping exact and wildcard rules',
         [((0, 0, 1, -1), 0), ((0, 0, 1, 1), 0)], LOAD_ROAD)
    pair(919, 'Commodity-specific rules leave other commodity unrestricted',
         [((0, 0, 1, 1), 1)], {(0, 0, 1, 1), (1, 1, 0, 0)}, group=0)

    for node in NODES:
        add(node, 920, -1, (0, 0, 1, -1), include=node == NODES[1])
        add(node, 920, 0, (0, 0, 3, 1), include=node == NODES[1])
    record(920, 'Commodity-specific set replaces generic commodity set', {
        (n, g): ({(0, 0, 3, 1)} if g == 0 else LOAD_ROAD)
        if n == NODES[1] else ALL - ({(0, 0, 3, 1)} if g == 0 else LOAD_ROAD)
        for n in NODES for g in (0, 1)
    })
    for node in NODES:
        add(node, 921, 0, (0, 0, 1, 1), include=node == NODES[1])
        add(node, 921, 1, (0, 0, 2, 1), include=node == NODES[1])
    record(921, 'Two commodities keep independent rule sets', {
        (n, g): {(0, 0, g + 1, 1)} if n == NODES[1] else ALL - {(0, 0, g + 1, 1)}
        for n in NODES for g in (0, 1)
    })
    maxima = {(0, 0, 1, 3), (0, 0, 2, 7), (0, 0, 3, 2)}
    pair(922, 'Highest available means of each mode', [(m, 0) for m in sorted(maxima)], maxima)
    pair(923, 'Duplicate rules do not change the selected movements',
         [((0, 0, 1, 1), 1), ((0, 0, 1, 1), 1)], {(0, 0, 1, 1), (1, 1, 0, 0)})
    pair(1000, 'Maximum supported scenario number', [((0, 0, 3, -1), 1)],
         {(0, 0, 3, m) for m in (1, 2)} | {(3, m, 0, 0) for m in (1, 2)})

    def literal(value):
        return "'" + value.replace("'", "''") + "'" if isinstance(value, str) else str(value)

    def insert(table, rows):
        return '\n'.join('INSERT INTO ' + table + ' VALUES ('
                         + ','.join(map(literal, row)) + ');' for row in rows)

    sql = '''-- Generated by generate.py. Creates new, inactive tables; refuses existing names.
-- num < 0: exclusion; num > 0: inclusion. -1: wildcard. (0,0): goods on site.
-- Link-layer MEANS is a count: road 1..3, IWW 1..7, rail 1..2.
CREATE TABLE nodus_rule_tests (num NUMERIC(11,0), scenario NUMERIC(4,0), grp NUMERIC(2,0), mode1 NUMERIC(2,0), means1 NUMERIC(2,0), mode2 NUMERIC(2,0), means2 NUMERIC(2,0), symetry NUMERIC(1,0));
CREATE TABLE nodus_rule_test_cases (scenario INTEGER, description VARCHAR(200));
CREATE TABLE nodus_rule_test_expected (num INTEGER, scenario INTEGER, grp INTEGER, mode1 INTEGER, means1 INTEGER, mode2 INTEGER, means2 INTEGER, excluded INTEGER);
'''
    sql += insert('nodus_rule_tests', rules) + '\n'
    sql += insert('nodus_rule_test_cases', cases) + '\n'
    sql += insert('nodus_rule_test_expected', expected) + '\n'
    (DIRECTORY / 'node_rules_test.sql').write_text(sql, encoding='utf-8')
    (DIRECTORY / 'cases.md').write_text(
        '# Centroid node-rule cases\n\n'
        f'{len(rules)} rules; {len(cases)} scenario cases; {len(expected)} expected decisions.\n\n'
        'Nodes: 1020201 (Antwerpen), 1020303 (Liège), centroids layer.\n'
        'Topology assumption: repository demo, handling 8 (load/unload, no transit), '
        'road mode 1 with means 1–3, IWW mode 2 with means 1–7, rail mode 3 with means 1–2. '
        'Live topology must be checked before installation.\n\n'
        'Except the fallback/isolation cases 900–901, node 1020201 excludes the selected '
        'movements and node 1020303 includes only them. Both commodity groups 0 and 1 '
        'are checked; group 1 can be tested without adding OD demand. '
        'Cases 902 and 914 deliberately reference transfers absent from these centroids.\n\n'
        '| Scenario | Case |\n| --- | --- |\n'
        + ''.join(f'| {scenario} | {title} |\n' for scenario, title in cases)
        + '\n`nodus_rule_test_expected.excluded`: 1 = forbidden by rules, 0 = permitted '
        'by rules. A permitted movement still needs a defined cost function to be usable.\n\n'
        'The tables are inactive until `exctable=nodus_rule_tests` is selected. '
        'Use the chosen scenario number with the usual assignment settings, then restore '
        'the original `exctable` value. The script does not create assignment configurations '
        'for these scenario numbers or overwrite existing assignment results. '
        'Inspect expected outcomes with:\n\n```sql\n'
        'SELECT * FROM nodus_rule_test_expected\n'
        'WHERE scenario = 905 AND grp = 0 ORDER BY num, mode1, means1, mode2, means2;\n```\n\n'
        'This is a representative interaction matrix, not every possible rule set. '
        'Transfer/transit behavior needs handling-enabled junctions; conflicting mixed '
        'inclusion/exclusion sets need a defined precedence policy.\n', encoding='utf-8')
    print(f'{len(rules)} rules, {len(cases)} cases, {len(expected)} expected decisions')


if __name__ == '__main__':
    generate()
