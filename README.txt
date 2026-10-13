MORROW FRONTEND PATCH

Contains the three frontend files to replace in your existing Morrow-Wallet-JDBC-Phase1 project:
- src/main/resources/templates/index.html
- src/main/resources/static/css/app.css
- src/main/resources/static/js/app.js

Copy these files into the same relative paths in your existing project and overwrite the old versions.
Then stop the running Spring Boot process with Ctrl+C, and restart from the project root:

mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"

Open http://localhost:8081

Important: sign-in, add-money, transfer, and transaction operations in this frontend are local demo preview behavior only. They do not authenticate against Oracle or persist data. The next implementation phase connects them to Java/Spring MVC and Oracle JDBC.
