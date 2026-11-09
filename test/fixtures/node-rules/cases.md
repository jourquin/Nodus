# Centroid node-rule cases

67 rules; 25 scenario cases; 2400 expected decisions.

Nodes: 1020201 (Antwerpen), 1020303 (Liège), centroids layer.
Topology assumption: repository demo, handling 8 (load/unload, no transit), road mode 1 with means 1–3, IWW mode 2 with means 1–7, rail mode 3 with means 1–2. Live topology must be checked before installation.

Except the fallback/isolation cases 900–901, node 1020201 excludes the selected movements and node 1020303 includes only them. Both commodity groups 0 and 1 are checked; group 1 can be tested without adding OD demand. Cases 902 and 914 deliberately reference transfers absent from these centroids.

| Scenario | Case |
| --- | --- |
| 900 | Generic scenario and group fallback |
| 901 | Scenario override is per node; missing group has no rules |
| 902 | Nonmatching transfer rules: exclusions allow; inclusions reject |
| 903 | Exact loading, one direction |
| 904 | Exact unloading, one direction |
| 905 | Exact loading and unloading via symmetry |
| 906 | All means of one mode, loading |
| 907 | All means of one mode, unloading |
| 908 | All means of one mode, symmetric |
| 909 | All modes and means, loading |
| 910 | All modes and means, unloading |
| 911 | All modes and means, symmetric |
| 912 | Wildcard origin endpoint to exact vehicle |
| 913 | Exact vehicle to wildcard destination endpoint |
| 914 | Wildcards at both transfer means; no centroid transfer links |
| 915 | Two loading rules form a union |
| 916 | Two unloading rules form a union |
| 917 | Loading and unloading rules with different vehicles |
| 918 | Overlapping exact and wildcard rules |
| 919 | Commodity-specific rules leave other commodity unrestricted |
| 920 | Commodity-specific set replaces generic commodity set |
| 921 | Two commodities keep independent rule sets |
| 922 | Highest available means of each mode |
| 923 | Duplicate rules do not change the selected movements |
| 1000 | Maximum supported scenario number |

`nodus_rule_test_expected.excluded`: 1 = forbidden by rules, 0 = permitted by rules. A permitted movement still needs a defined cost function to be usable.

The tables are inactive until `exctable=nodus_rule_tests` is selected. Use the chosen scenario number with the usual assignment settings, then restore the original `exctable` value. The script does not create assignment configurations for these scenario numbers or overwrite existing assignment results. Inspect expected outcomes with:

```sql
SELECT * FROM nodus_rule_test_expected
WHERE scenario = 905 AND grp = 0 ORDER BY num, mode1, means1, mode2, means2;
```

This is a representative interaction matrix, not every possible rule set. Transfer/transit behavior needs handling-enabled junctions; conflicting mixed inclusion/exclusion sets need a defined precedence policy.
