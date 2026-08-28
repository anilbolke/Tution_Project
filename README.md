# Tuition Management System

A Java web application (JSP + Servlet + MySQL) for managing a coaching / tuition
institute — admissions, slab-wise fees, payments, attendance, exams and results.
All pages share the green "Havellsson NEET Samrat" theme.

### Features so far
- **Public inquiry form** (`/inquiry.jsp`) — prospective students leave details; no login needed (linked from the login page).
- **Staff login** (`/login.jsp`) — session-based authentication, SHA-256 hashed passwords.
- **Dashboard** (`/dashboard.jsp`) — module launcher.
- **Admission form** (`/admission.jsp`) — full student registration with photo upload, fee slabs and a live payment schedule.
- **Student list** (`/students`) — all admitted students in a table.

### Routes
| Path           | Auth | Purpose                                  |
|----------------|------|------------------------------------------|
| `/inquiry.jsp` | No   | Public inquiry form                      |
| `/inquiry`     | No   | Saves an inquiry (POST)                  |
| `/login.jsp`   | No   | Login page                               |
| `/login`       | No   | Authenticate (POST)                      |
| `/logout`      | —    | Ends session                             |
| `/dashboard.jsp` | Yes | Module launcher                         |
| `/admission.jsp` | Yes | Admission form                          |
| `/admission`   | Yes  | Saves an admission (POST, multipart)     |
| `/students`    | Yes  | List all admitted students               |

## Tech stack

| Layer    | Technology                         |
|----------|------------------------------------|
| Frontend | JSP, HTML5, CSS3                   |
| Backend  | Java Servlets (`javax.servlet`)    |
| Server   | Apache Tomcat 9                    |
| Database | MySQL 8                           |
| JDK      | Java 21 (Java 11+ works)          |

> **Note on Tomcat version:** This project targets **Tomcat 9**, which uses the
> `javax.servlet.*` API (Servlet 4.0). If you deploy to **Tomcat 10+**, the
> imports must change to `jakarta.servlet.*`.

## Project structure

```
Tution_Project/
├── database/
│   └── schema.sql                 # MySQL database + tables + seed admin
├── src/main/java/com/tution/
│   ├── dao/UserDAO.java           # authentication query
│   ├── model/User.java            # user bean
│   ├── servlet/LoginServlet.java  # POST /login
│   ├── servlet/LogoutServlet.java # GET  /logout
│   └── util/
│       ├── DBConnection.java      # JDBC connection (edit credentials here)
│       └── PasswordUtil.java      # SHA-256 hashing
└── src/main/webapp/
    ├── css/style.css              # shared green theme
    ├── login.jsp                  # login page
    ├── dashboard.jsp              # post-login landing
    └── WEB-INF/
        ├── web.xml                # Servlet 4.0 config
        └── lib/                   # <-- put mysql-connector-j.jar here
```

## Setup

### 1. Create the database
```bash
mysql -u root -p < database/schema.sql
```
This creates the `tuition_db` database, the `users` / `students` / `fee_slabs`
tables, and a default admin account.

### 2. Add the MySQL JDBC driver
Download **MySQL Connector/J 8.x** (`mysql-connector-j-8.x.x.jar`) and copy it into:
```
src/main/webapp/WEB-INF/lib/
```

### 3. Configure DB credentials
Edit `src/main/java/com/tution/util/DBConnection.java` and set `USER` / `PASS`
to match your MySQL installation.

### 4. Run on Tomcat 9
- In Eclipse: right-click the project → **Run As → Run on Server** → Tomcat 9.
- Or export a WAR and drop it into `tomcat/webapps/`.

### 5. Open in the browser
```
http://localhost:8080/Tution_Project/
```

## Default login

| Username | Password   |
|----------|------------|
| `admin`  | `admin123` |

Passwords are stored as **SHA-256 hashes** (`SHA2(..., 256)` in MySQL, matching
`PasswordUtil.sha256()` in Java). Authentication uses a parameterised query to
prevent SQL injection.
