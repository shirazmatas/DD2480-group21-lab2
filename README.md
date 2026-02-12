# Continuous Integration Server

#### Java program that runs automated builds and tracks build history for GitHub repositories

This project implements a continuous integration server in Java. It listens for GitHub webhook `push` events and automatically:

- clones the repository at the latest commit;
- compiles the project using Maven;
- runs all JUnit tests using Maven;
- updates the GitHub commit status.

The build logs and metadata are stored locally and can be viewed online at the server URL.

## Usage

### Dependencies
- JDK 21+
- JUnit 5
- Maven 3.8+
- Jetty 9+
- Servlet API 4+
- org.json

### Build and run
To compile:
```bash
mvn compile
```
To run tests:
```bash
mvn test
```
To start server:
```bash
java -cp target/classes;dependencies ContinuousIntegrationServer
```
To run locally, use a service like ngrok to tunnel to port 8080, and configure the GitHub repository webhook to point to the server URL at the `/webhook` endpoint for `push` events with the format `application/json`. The build history can be viewed at the server URL.

### Example server
The URL of the example server running on our machine is [adoptively-worldwide-myah.ngrok-free.dev](adoptively-worldwide-myah.ngrok-free.dev).

### Viewing Javadocs
1. Go into /docs.
2. Open index.html.


## Contributions
This project was developed by Group 21:
- **Barnabas Tanczos:** ContinuousIntegrationServer request handling, Build class skeleton, compilation, logging, cleanup, README
- **Lucas Lund:** commit status update, documentation
- **Shengye (Óscar) Huang Wu:** automatic tests, test cases, SEMAT checklist

## License
This project is licensed under the MIT License. You are free to use, modify, and distribute this software in accordance with the terms described in the LICENSE file included in this repository.