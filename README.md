# Crypto Dashboard

A JavaFX desktop application that displays cryptocurrency data and related news.

## Prerequisites

- Java 17 (JDK 17) installed and JAVA_HOME configured
- Maven 3.6+ (for building and running via the JavaFX Maven plugin)

## Application properties

For the news data a SerpApi key is needed and set in `src/main/resources/application.properties` to `serp.api.key`.

## Build

To compile the project and package it (tests run by default):

```bash
mvn package
```

This creates build artifacts in `target/`, including a `jar-with-dependencies` assembly.

## Run

```bash
mvn javafx:run
```

## Tests

Run unit tests with:

```bash
mvn test
```

## Known issues

- For the news section, All sometimes shows coin specific news, but is fixed when toggled between Selected and back to All.
- For the volume data chart, the x-axis labels are sometimes to the side of the chart, this is fixed by toggling to price and back to volume.

## Project structure

- `src/main/java` — application sources (UI, services, models)
- `src/main/resources` — CSS and other resources
- `src/test/java` — unit tests
- `pom.xml` — Maven build configuration
