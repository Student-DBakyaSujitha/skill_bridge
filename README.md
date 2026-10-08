SkillBridge
Compare your skills with a chosen career, get a learning roadmap and portfolio project ideas.

## Run

Requires Java 17 or newer. Maven Wrapper downloads and runs the required Maven version automatically:

```powershell
.\mvnw.cmd spring-boot:run
```

Open http://127.0.0.1:8080. To build a runnable JAR, use
`.\mvnw.cmd package` and then `java -jar target/skillbridge-1.0.0.jar`.
On macOS/Linux, use `./mvnw` instead of `.\mvnw.cmd`.

Pages: `/` home, `/register`, `/login`, `/dashboard`, `/profile`. Accounts live in `instance/skillbridge.db`.
The Java server continues to use the existing SQLite database and account password hashes.
Before deploying, serve over HTTPS and set `PRODUCTION=1` so session cookies are Secure.
Career requirements and aliases are maintained in `CareerCatalog.java`.