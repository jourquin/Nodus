
# Compute the R squared between observed and estimated quantities per mode and OD relation

# Assignment to test
@@inputTable := demo_path5_header;

# Mode to test
@@mode :=  3;

# Prepare output table with estimated quantities
drop table if exists ODCorr;
create table ODCorr as (select org, dst, grp, ldmode as mode, sum(qty) as est_qty from @@inputTable group by org, dst, grp, mode) with data;
alter table ODCorr add obs_qty DECIMAL(13,3) default 0;
create index idx on ODCorr (org, dst, grp);

# Add observed quantities
update ODCorr as a set obs_qty = (select qty from od_road as b where a.org=b.org and a.dst=b.dst and a.grp=b.grp) where mode = 1;
update ODCorr as a set obs_qty = (select qty from od_iww as b where a.org=b.org and a.dst=b.dst and a.grp=b.grp) where mode = 2;
update ODCorr as a set obs_qty = (select qty from od_rail as b where a.org=b.org and a.dst=b.dst and a.grp=b.grp) where mode = 3;
update ODcorr set obs_qty = 0 where obs_qty is null;

# Compute R squared
drop table if exists tmp;
create table tmp as (select * from ODCorr where mode = @@mode) with data;
select power(sum( 
(obs_qty - (select avg(obs_qty) from tmp) ) * (est_qty - (select avg(est_qty) from tmp) ) /
( ((select count(obs_qty) from tmp) - 1) * (select stddev_pop(obs_qty) from tmp) * (select stddev_pop(est_qty) from tmp))
),2) from tmp;
drop table if exists tmp;

