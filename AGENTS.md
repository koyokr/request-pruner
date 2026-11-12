# Request Pruner

## GitHub Releases

When asked to publish a JAR to GitHub Releases without an explicit version, check whether a release already exists for the project's current version. If it does, increment the patch version by 1 (`1.2.3` → `1.2.4`) until an unused release version is found, update the project's version, rebuild the JAR, and publish under the matching tag. If the user specifies a version, use that version without automatically incrementing it.
