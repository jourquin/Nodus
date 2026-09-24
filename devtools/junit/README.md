# Test dependency

`apiguardian-api-1.1.2.jar` supplies the API annotations used by JUnit 5, avoiding missing
annotation warnings when compiling the tests. It is used only by the test build and
Eclipse test source folder; it is not an application dependency.

- Project: https://github.com/apiguardian-team/apiguardian
- Source: https://repo.maven.apache.org/maven2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar
- License: Apache License 2.0, included in the jar as `META-INF/LICENSE` and alongside it as `LICENSE`.
- SHA-256: `b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38`

JUnit Jupiter, its platform libraries, and OpenTest4J are already bundled in `lib/groovy/`.
The Ant JUnit launcher task comes from the installed Apache Ant distribution.
