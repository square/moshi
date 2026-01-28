# Releasing

Cutting a Release
-----------------

1. Update the `CHANGELOG.md`:
   1. Change the `Unreleased` header to the release version.
   2. Add a link URL to ensure the header link works.
   3. Add a new `Unreleased` section to the top.

2. Set versions:

    ```
    export RELEASE_VERSION=X.Y.Z
    export NEXT_VERSION=X.Y.Z-SNAPSHOT
    ```

3. Update versions, tag the release, and prepare for the next release.

    ```bash
    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$RELEASE_VERSION/g" \
      gradle.properties
    sed -i "" \
      "s/\"com.squareup.moshi:\([^\:]*\):[0-9.]*\"/\"com.squareup.moshi:\1:$RELEASE_VERSION\"/g" \
      `find . -name "README.md"`

    git commit -am "Prepare version $RELEASE_VERSION."
    git tag -am "Version $RELEASE_VERSION" $RELEASE_VERSION

    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$NEXT_VERSION/g" \
      gradle.properties
    git commit -am "Prepare next development version."
    git push && git push --tags
    ```

This will trigger a GitHub Action workflow which will create a GitHub release and upload the
release artifacts to Maven Central.
