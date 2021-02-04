# This is tentative to compile Nodus binaries using GraalVM's native-image tool
# Does'nt work for now :
# - the H2 jar cannot be included due to some, variable declarations issues
# - AWT is not included in the binary (known issue by the GraalVM community)
# - Initialization problem with the DBF lib. Solved using the --initialize-at-build-time option ?
# 


native-image \
--allow-incomplete-classpath \
--initialize-at-build-time=edu.uclouvain.core.nodus.database.dbf.DBFWriter,edu.uclouvain.core.nodus.database.dbf.DBFReader,edu.uclouvain.core.nodus.database.dbf.DBFBase,edu.uclouvain.core.nodus.database.dbf.DBFUtils \
--report-unsupported-elements-at-runtime \
--no-fallback  \
-cp classes:lib/*:lib/groovy/* \
edu.uclouvain.core.nodus.Nodus nodus8bin
