Releasing
=========

### Prerequisite: Sonatype (Maven Central) Account

Create an account on the [Sonatype issues site][sonatype_issues]. Ask an existing publisher to open
an issue requesting publishing permissions for `com.squareup` projects.


Cutting a Release
-----------------

1. Update `CHANGELOG.md`.

2. Set versions:

    ```
    export RELEASE_VERSION=X.Y.Z
    export NEXT_VERSION=X.Y.Z-SNAPSHOT
    ```

3. Update versions:

    ```
    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$RELEASE_VERSION/g" \
      gradle.properties
    sed -i "" \
      "s/\"com.squareup.moshi:\([^\:]*\):[^\"]*\"/\"com.squareup.moshi:\1:$RELEASE_VERSION\"/g" \
      `find . -name "README.md"`
    ```

4. Tag the release and push to GitHub.

    ```
    git commit -am "Prepare for release $RELEASE_VERSION."
    git tag -a parent-$RELEASE_VERSION -m "Version $RELEASE_VERSION"
    git push && git push --tags
    ```

5. Wait for [GitHub Actions][github_actions] to start building the release.

6. Prepare for ongoing development and push to GitHub.

    ```
    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$NEXT_VERSION/g" \
      gradle.properties
    git commit -am "Prepare next development version."
    git push
    ```

7. Wait for [GitHub Actions][github_actions] to build and publish releases for both Windows and
   Non-Windows.

8. Visit [Sonatype Nexus][sonatype_nexus] to promote (close then release) the releases. Or drop it
   if there is a problem!

 [github_actions]: https://github.com/square/moshi/actions
 [sonatype_issues]: https://issues.sonatype.org/
 [sonatype_nexus]: https://oss.sonatype.org/
