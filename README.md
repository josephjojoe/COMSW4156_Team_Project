# COMSW4156_Runtime_Terrors_Project

This is the GitHub repository for the Team Project associated with COMS 4156 Advanced Software Engineering. Our team name is **Runtime Terrors** and the following are our members: Lizette Hernandez, Meron Belachew, Alessandro Castillo, Katherine Newton, and Joseph Jojoe.

# Viewing the Project Repository
___
Please use the following link to view the repository relevant to the app: https://github.com/krn2125/COMSW4156_Team_Project

# Building and Running a Local Instance
___
To build and use this service you must install the following (This guide assumes MacOS):
1. Maven 3.9.11: : https://maven.apache.org/download.cgi Download and follow the installation instructions
2. JDK 19: This project used JDK 19 for development so that is what we recommend you use: https://www.oracle.com/java/technologies/javase/jdk19-archive-downloads.html
3. IntelliJ IDE: We recommend using IntelliJ but you are free to use any other IDE that you are comfortable with: https://www.jetbrains.com/idea/download/?section=windows
4. If you wish to run the style checker you can with mvn checkstyle:check or mvn checkstyle:checkstyle if you wish to generate the report.

The endpoints are listed below in the "Endpoints" section, with brief descriptions of their parameters. For in-depth examples and system-level tests of them, see the section "Postman Test Documentation" below.

# Running the Service
___
# Running Tests
___
# Endpoints
___
# Style Checking Report
___
# Branch Coverage Reporting
___
# Static Code Analysis
___
# Tools used
___

This section includes notes on tools and technologies used in building this project.

- Maven Package Manager
- GitHub Actions CI
    - This is enabled via the "Actions" tab on GitHub.
    - Currently on push and pull to the main branch the project is built and the test are ran
- Checkstyle
    - We use Checkstyle for code reporting.
- PMD
    - We are using PMD to do static analysis of our Java code.
- JUnit
    - JUnit tests get run automatically as part of the CI pipeline.
- JaCoCo
    - We use JaCoCo for generating code coverage reports.
- Postman
    - We used Postman for testing that the APIs work.
- Mockito
    - We use Mockito to create mock objects and isolate unit tests from external dependencies.
