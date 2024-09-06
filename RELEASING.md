# Releasing

1. Submit a PR with the following changes (see e.g. #4):
   * Update `version` in `gradle.properties` to the release version.
   * Update the `CHANGELOG.md`. Create a section for the new version and move the unreleased version there
   * Update the `README.md` as necessary to reflect the new release version.

2. Once merged, pull latest into main locally. Tag version:

   ```
   $ git tag -am "Version X.Y.Z" X.Y.Z
   ```

3. Push tags:

   ```
   $ git push --tags
   ```

4. Create GitHub Release
    1. Visit the [New Releases](https://github.com/collectiveidea/oauth-kmp/releases/new) page.
    2. Supply release version and changelog link

5. Publish

   ```
   $ ./gradlew publish
   ```

6. Visit [Sonatype Nexus](https://s01.oss.sonatype.org) and promote the artifact. (Close the staging repository, then Release it)
