# Node-rule combination fixture

`node_rules_test.sql` contains 67 rules for centroid nodes 1020201 and 1020303,
25 scenario cases and 2,400 independently enumerated expected loading/unloading
decisions. [cases.md](cases.md) describes the scenarios and topology assumptions.

`NodeRulesTableIntegrationTest` loads this fixture into isolated H2 and HSQLDB
databases, with original and reversed rule insertion order. It does not access
an open Nodus project or depend on the original script output directory.

To regenerate the SQL and case matrix from the repository root:

```sh
python3 test/fixtures/node-rules/generate.py
```

Keep these fixtures with the tests. Assignment audit reports in the original
`scripts/node-rules-test/` directory are optional and may be deleted.
