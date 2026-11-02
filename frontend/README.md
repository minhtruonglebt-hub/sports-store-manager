# Sportiva Next.js frontend

The frontend is a Next.js App Router application. It calls the existing Spring Boot REST API through a same-origin rewrite, preserving the API's session login and CSRF protection.

## Run locally

1. Start MySQL in XAMPP.
2. In a terminal at `C:\Users\ACER\sports-store-manager`, run `mvn spring-boot:run`.
3. In a second terminal at `C:\Users\ACER\sports-store-manager\frontend`, run `npm install` once, then `npm run dev`.
4. Open `http://localhost:3000` and sign in with the demo account shown on the login page.

Spring Boot remains on port `8081`; its API and MariaDB configuration stay in the backend project.
