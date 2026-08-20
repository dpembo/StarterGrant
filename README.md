# StarterGrant

A small Paper plugin that gives a one-time starter grant (e.g. kit token + opening balance) the first time a player joins the server.

Extracted from the starter-grant feature of FixedSpawn.

## Features

- Runs configurable console commands once per player (UUID-tracked).
- Two storage backends: **file** or **mariadb**.
- Async check/record so join is not blocked by I/O.
- Race-safe (flag written before commands; MariaDB uses INSERT IGNORE).
- `/startergrant reload`

## Requirements

- Paper 26.2
- Java 25+

## Building

mvn package

Jar: target/StarterGrant.jar