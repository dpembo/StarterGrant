# StarterGrant

A small Paper plugin that gives a one-time starter grant (e.g. kit token + opening balance) the first time a player joins the server.

Extracted from the starter-grant feature of FixedSpawn.

## Features

- Runs configurable console commands once per player (UUID-tracked).
- Two storage backends: **file** or **mariadb**.
- Async check/record so join is not blocked by I/O.
- Race-safe (flag written before commands; MariaDB uses INSERT IGNORE).
- `/startergrant reload`
- `/startergrant migrate` copies every record from the active backend to the other one, so you can switch `storage.type`. MariaDB → file rewrites the file with those UUIDs. File → MariaDB inserts any UUID not already in the table (existing rows are kept, so a player already granted on the database is not granted again).

## Requirements

- Paper 26.2
- Java 25+

## Building

mvn package

Jar: target/StarterGrant-<version>.jar (for example target/StarterGrant-1.1.0.jar)