# Releasing

1. Update `version` in `gradle.properties` to the release version.

2. Update the `CHANGELOG.md`.

3. Update the `README.md` to reflect the new release version number.

4. Commit

   ```
   $ git commit -am "Prepare version X.Y.Z"
   ```

5. Tag

   ```
   $ git tag -am "Version X.Y.Z" X.Y.Z
   ```

6. Push!

   ```
   $ git push && git push --tags
   ```

7. Create GitHub Release
    1. Visit the [New Releases](https://github.com/collectiveidea/oauth-kmp/releases/new) page.
    2. Supply release version and changelog

8. Publish (runtime)

   ```
   $ ./gradlew publish
   ```

9. Visit [Sonatype Nexus](https://s01.oss.sonatype.org) and promote the artifact.