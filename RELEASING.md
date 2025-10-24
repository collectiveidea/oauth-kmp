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

6. Promote from Staging

   ```
   source ~/.gradle/gradle.properties
   repository_key=$(curl -u $SONATYPE_USERNAME:$SONATYPE_PASSWORD -X GET "https://ossrh-staging-api.central.sonatype.com/manual/search/repositories?ip=any&profile_id=com.collectiveidea" | jq --raw-output ".repositories[0].key")
   curl -u $SONATYPE_USERNAME:$SONATYPE_PASSWORD -X POST "https://ossrh-staging-api.central.sonatype.com/manual/upload/repository/$repository_key"
   ```

7. Visit https://central.sonatype.com/publishing/deployments and press Publish
