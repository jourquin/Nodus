drop table if exists od;
drop table if exists tmp;
create table tmp as (select * from od_road union all select * from  od_iww union all select * from  od_rail) with data;
create table  od as (select grp, org, dst, sum(qty) as qty from tmp group by grp, org, dst) with data;
drop table tmp;
