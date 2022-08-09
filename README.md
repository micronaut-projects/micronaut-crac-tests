# Micronaut CRaC tests

This repository allows testing of Micronaut integration with CRaC.

## Tun all the tests

To run all the tests, simply run

```shell
$ ./gradlew build
```

This will generate all the projects in `build/code` and run the test scripts.

To test a single test, run the dynamic task created by `CracPlugin`; convert the kebab case guide directory name to lowerCamelCase and add "Build", e.g. to build `netty-data`, run

```shell
./gradlew nettyDataBuild
```

## Create a new guide

All the tests leverage [Micronaut Starter](https://github.com/micronaut-projects/micronaut-starter) core to create the projects. The idea is that one guide can generate up to six different projects, one per language (Java, Groovy and Kotlin) and build tool (Gradle and Maven).

### Guide structure

All the guides are in the `guides` directory in separate subdirectories. Inside the directory, the main file is `metadata.json` that describes the guide. All the fields are declared in [GuideMetadata](https://github.com/micronaut-projects/micronaut-guides/blob/master/buildSrc/src/main/groovy/io/micronaut/guides/GuideMetadata.groovy) class.

```json
{
  "apps": [
    {
      "name": "default",
      "features": ["graalvm", "reactor"]
    }
  ]
}
```

Besides, the obvious fields that don't need any further explanation, the other are:

- `buildTools`: By default we generate the code in the guides for Gradle and Maven. If a guide is specific only for a build tool, define it here.
- `languages`: The guides should be written in the three languages. Sometimes we only write guides in one language or the guide only supports a specific language.
- `skipGradleTests`: Set it to `true` to skip running the tests for the Gradle applications for the guide. This is useful when it's not easy to run tests on CI, for example for some cloud guides.
- `skipMavenTests`: Same as `skipGradleTests` but for Maven applications.
- `apps`: List of pairs `name`-`features` for the generated application. There are two types of guides, most of the guides only generate one application (single-app). In this case the name of the applications needs to be `default`. There are a few guides that generate multiple applications, so they need to be declared here:
```json
  ...
  "apps": [
    {
      "name": "bookcatalogue",
      "features": ["tracing-jaeger", "management"]
    },
    {
      "name": "bookinventory",
      "features": ["tracing-jaeger", "management"]
    },
    {
      "name": "bookrecommendation",
      "features": ["tracing-jaeger", "management", "reactor"]
    }
  ]
```
The features need to be **valid** features from Starter because the list is used directly when generating the applications using Starter infrastructure. If you need a feature that is not available on Starter, create it in `buildSrc/src/main/java/io/micronaut/guides/feature`. Also declare the GAV coordinates and version in `buildSrc/src/main/resources/pom.xml`. Dependabot is configured in this project to look for that file and send pull requests to update the dependencies.

Inside the specific guide directory there should be a directory per language with the appropriate directory structure. All these files will be copied into the final guide directory after the guide is generated.

```shell
netty-data
├── groovy
│     └── src
│         ├── main
│         │     └── groovy
│         │         └── example
│         │             └── micronaut
│         └── test
│             └── groovy
│                 └── example
│                     └── micronaut
├── java
│     └── src
│         ├── main
│         │     └── java
│         │         └── example
│         │             └── micronaut
│         └── test
│             └── java
│                 └── example
│                     └── micronaut
├── kotlin
│     └── src
│         ├── main
│         │     └── kotlin
│         │         └── example
│         │             └── micronaut
│         └── test
│             └── kotlin
│                 └── example
│                     └── micronaut
└── src
    └── main
        └── resources
```

For multi-applications guides there needs to be an additional directory with the name of the application declared in `metadata.json` file:

```shell
netty-data
├── bookcatalogue
│     ├── groovy
│     │    ...
│     ├── java
│     │    ...
│     └── kotlin
│          ...
├── bookinventory
│     ├── groovy
│     │    ...
│     ├── java
│     │    ...
│     └── kotlin
│          ...
└── bookrecommendation
      ├── groovy
      │    ...
      ├── java
      │    ...
      └── kotlin
```

## GitHub Actions

There is a single job which Runs everytime we send a pull request or something is merged in `master`. The `test.sh` script explained before is executed.
