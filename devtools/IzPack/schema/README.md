# Local IzPack schemas

These schemas come from `schema/5.0/` in the bundled
`../izpack-compiler-5.2.4.jar`. The installer definition and its English and French
language packs reference these files so XML editors can validate them without
downloading external resources.

The installation schema imports the types schema using a local relative path;
that is the only change to the upstream schema definitions. The namespace URIs
remain unchanged. Copyright notices are retained in each file; the Apache 2.0
license is in [IzPack-Licence.txt](../IzPack-Licence.txt).

When upgrading IzPack, extract the installation, types and langpack schemas from
the matching compiler JAR and make the installation schema's types import local
again. Check for any new imports or includes and retain those dependencies
locally too. Validate `installer/installer.xml`, `installer/packsLang_eng.xml` and
`installer/packsLang_fra.xml` after updating the schemas.
