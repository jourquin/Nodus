"""Regenerate the tiny synthetic example (development only: duckdb and pyshp).

No research data or Nodus database is read. Runtime extraction needs neither Python
nor these packages. Run from any directory; optionally pass an empty output folder.
"""
from pathlib import Path
import argparse
import hashlib
import json
import duckdb
import shapefile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path)
args = parser.parse_args()
root = args.output or Path(__file__).resolve().parents[3] / 'scripts/master-od-sample'
root.mkdir(parents=True, exist_ok=True)

sql = '''-- Synthetic annual tonnes, not observed traffic.
-- Portable CREATE TABLE and single-row INSERT statements for HSQLDB, H2,
-- MySQL/MariaDB and DuckDB. Creates a new table named sample.
-- Optional example only. Nodus Parquet extraction does not execute this file.

CREATE TABLE sample (
    GRP SMALLINT NOT NULL,
    ORG BIGINT NOT NULL,
    DST BIGINT NOT NULL,
    ORG_L2 BIGINT NOT NULL,
    DST_L2 BIGINT NOT NULL,
    CHAPTER SMALLINT NOT NULL,
    QTY_AVAILABLE_PART DOUBLE PRECISION,
    UNESTIMATED_CELLS INTEGER NOT NULL,
    PROXY_APPLIED BOOLEAN NOT NULL,
    PROXY_QTY DOUBLE PRECISION,
    SPATIAL_REVIEW BOOLEAN NOT NULL
);

INSERT INTO sample
    (GRP, ORG, DST, ORG_L2, DST_L2, CHAPTER, QTY_AVAILABLE_PART,
     UNESTIMATED_CELLS, PROXY_APPLIED, PROXY_QTY, SPATIAL_REVIEW)
VALUES (0, 101, 101, 10, 10, 0, 1.25, 0, FALSE, 0.0, FALSE);

INSERT INTO sample
    (GRP, ORG, DST, ORG_L2, DST_L2, CHAPTER, QTY_AVAILABLE_PART,
     UNESTIMATED_CELLS, PROXY_APPLIED, PROXY_QTY, SPATIAL_REVIEW)
VALUES (0, 101, 102, 10, 10, 0, 2.75, 0, TRUE, 2.75, TRUE);

INSERT INTO sample
    (GRP, ORG, DST, ORG_L2, DST_L2, CHAPTER, QTY_AVAILABLE_PART,
     UNESTIMATED_CELLS, PROXY_APPLIED, PROXY_QTY, SPATIAL_REVIEW)
VALUES (0, 102, 201, 10, 20, 0, NULL, 1, FALSE, NULL, TRUE);

INSERT INTO sample
    (GRP, ORG, DST, ORG_L2, DST_L2, CHAPTER, QTY_AVAILABLE_PART,
     UNESTIMATED_CELLS, PROXY_APPLIED, PROXY_QTY, SPATIAL_REVIEW)
VALUES (0, 201, 101, 20, 10, 0, 0.0, 0, FALSE, 0.0, FALSE);

INSERT INTO sample
    (GRP, ORG, DST, ORG_L2, DST_L2, CHAPTER, QTY_AVAILABLE_PART,
     UNESTIMATED_CELLS, PROXY_APPLIED, PROXY_QTY, SPATIAL_REVIEW)
VALUES (1, 101, 102, 10, 10, 0, 4.0, 0, FALSE, 0.0, FALSE);

INSERT INTO sample
    (GRP, ORG, DST, ORG_L2, DST_L2, CHAPTER, QTY_AVAILABLE_PART,
     UNESTIMATED_CELLS, PROXY_APPLIED, PROXY_QTY, SPATIAL_REVIEW)
VALUES (1, 102, 101, 10, 10, 0, 5.0, 2, FALSE, 0.0, FALSE);

INSERT INTO sample
    (GRP, ORG, DST, ORG_L2, DST_L2, CHAPTER, QTY_AVAILABLE_PART,
     UNESTIMATED_CELLS, PROXY_APPLIED, PROXY_QTY, SPATIAL_REVIEW)
VALUES (10, 101, 201, 10, 20, 1, 6.0, 0, FALSE, 0.0, FALSE);

INSERT INTO sample
    (GRP, ORG, DST, ORG_L2, DST_L2, CHAPTER, QTY_AVAILABLE_PART,
     UNESTIMATED_CELLS, PROXY_APPLIED, PROXY_QTY, SPATIAL_REVIEW)
VALUES (10, 102, 201, 10, 20, 1, 7.0, 0, FALSE, 0.0, FALSE);
'''
(root / 'sample_rows.sql').write_text(sql)
master = root / 'sample_road.parquet'
with duckdb.connect() as con:
    con.execute(sql)
    escaped = str(master).replace("'", "''")
    con.execute(f'''COPY (SELECT GRP::INTEGER GRP,ORG::BIGINT ORG,DST::BIGINT DST,
    ORG_L2::BIGINT ORG_L2,DST_L2::BIGINT DST_L2,CHAPTER::INTEGER CHAPTER,
    QTY_AVAILABLE_PART::DOUBLE QTY_AVAILABLE_PART,UNESTIMATED_CELLS::INTEGER UNESTIMATED_CELLS,
    PROXY_APPLIED::BOOLEAN PROXY_APPLIED,PROXY_QTY::DOUBLE PROXY_QTY,
    SPATIAL_REVIEW::BOOLEAN SPATIAL_REVIEW FROM sample ORDER BY GRP,ORG,DST)
    TO '{escaped}' (FORMAT PARQUET,COMPRESSION SNAPPY)''')

zones = [
    {'id':10, 'level':2, 'code':'AA01', 'parent':0},
    {'id':20, 'level':2, 'code':'AA02', 'parent':0},
    {'id':101, 'level':3, 'code':'AA011', 'parent':10},
    {'id':102, 'level':3, 'code':'AA012', 'parent':10},
    {'id':201, 'level':3, 'code':'AA021', 'parent':20},
]
xy = {10:(4.05,50.02),20:(4.22,50.08),101:(4.0,50.0),102:(4.1,50.05),201:(4.25,50.1)}
prj = 'GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563]],PRIMEM["Greenwich",0],UNIT["degree",0.0174532925199433]]\n'
for level in [2,3]:
    name = root / f'centroidsl{level}'
    with shapefile.Writer(str(name), shapeType=shapefile.POINT, encoding='utf-8') as writer:
        for field in [('NUM','N',10,0),('STYLE','N',2,0),('TRANSHIP','N',1,0),
                      ('LEVEL','N',1,0),('ZONE_CODE','C',8,0),('PARENT_ID','N',10,0)]:
            writer.field(*field)
        for zone in zones:
            if zone['level'] == level:
                writer.point(*xy[zone['id']])
                writer.record(zone['id'],0,8,level,zone['code'],zone['parent'])
    name.with_suffix('.cpg').write_text('UTF-8\n')
    name.with_suffix('.prj').write_text(prj)
with shapefile.Writer(str(root / 'roads'), shapeType=shapefile.POLYLINE, encoding='utf-8') as writer:
    for field in [('NUM','N',10,0),('STYLE','N',2,0),('ENABLED','N',1,0),('NODE1','N',10,0),
                  ('NODE2','N',10,0),('MODE','N',2,0),('MEANS','N',2,0),('CAPACITY','N',10,0),
                  ('SPEED','N',7,2)]:
        writer.field(*field)
    for index, (org, dst) in enumerate([(101,102),(102,201),(10,101),(20,201),(10,20)],1):
        writer.line([[xy[org],xy[dst]]])
        writer.record(index,0,1,org,dst,1,1,2000,50)
(root / 'roads.cpg').write_text('UTF-8\n')
(root / 'roads.prj').write_text(prj)
(root / 'sample.nodus').write_text('''# Synthetic project for demonstrating OD extraction, not calibrated assignments.
network.nodes=centroidsl2 centroidsl3
network.links=roads
centroidsl2.prettyName=Sample NUTS2 centroids (fictional)
centroidsl3.prettyName=Sample NUTS3 centroids (fictional)
roads.prettyName=Sample roads (fictional)
''')

catalog = {
    'schema_version':1, 'release':'synthetic_sample_v1', 'source_release':'synthetic_example',
    'year':2024, 'unit':'annual tonnes', 'supported_nuts_levels':[2,3],
    'classifications':['NSTR','CHAPTER'],
    'description':'Entirely fictional values and AA zone codes; no actual traffic observations.',
    'commodities':[{'id':0,'chapter':0},{'id':1,'chapter':0},{'id':10,'chapter':1},{'id':99,'chapter':9}],
    'zones':zones, 'modes':{'road':{
        'file':master.name, 'bytes':master.stat().st_size,
        'sha256':hashlib.sha256(master.read_bytes()).hexdigest(), 'rows':8, 'available_tonnes':26.0,
        'unestimated_components':3, 'unavailable_quantity_rows':1,
        'groups':[
            {'id':0,'rows':4,'available_tonnes':4.0,'unestimated_components':1,'unavailable_quantity_rows':1},
            {'id':1,'rows':2,'available_tonnes':9.0,'unestimated_components':2,'unavailable_quantity_rows':0},
            {'id':10,'rows':2,'available_tonnes':13.0,'unestimated_components':0,'unavailable_quantity_rows':0}
        ], 'source_absent_nstr_groups':[99]
    }}
}
(root / 'catalog.json').write_text(json.dumps(catalog,indent=2)+'\n')
print(f'Wrote {master.stat().st_size:,}-byte Parquet with 8 rows, 26 known tonnes, plus catalogue and miniature project.')
