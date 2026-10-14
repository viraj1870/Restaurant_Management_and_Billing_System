# Restaurant Management and Billing System

Web app for running a restaurant: menu, orders, kitchen, billing, payments, coupons, loyalty points, notifications, feedback and reports.

**Stack:** Java 21, Spring Boot 3, Spring Data JPA, Spring Security (JWT + BCrypt), MySQL, HTML/CSS/JS, Bootstrap 5.

## Run

### With MySQL (default)
1. Start MySQL (`MySQL80` service). The `restaurant_db` database is created automatically.
2. Set your credentials (defaults: `root` / `root`):
   ```powershell
   $env:DB_USERNAME="root"; $env:DB_PASSWORD="your_password"
   .\mvnw.cmd spring-boot:run
   ```
### Without MySQL (H2 file database)
```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=h2"
```
Open http://localhost:8080

### Tests
```powershell
.\mvnw.cmd test
```

## Demo accounts (password `Demo@123`)
| Role | Login |
|---|---|
| Merchant | merchant@demo.com |
| Customer | customer@demo.com (100 loyalty points) |
| Kitchen staff | kitchen@demo.com |

Demo coupons: `WELCOME10` (10%, max ₹100, once per customer), `FLAT50` (₹50 off above ₹500), `OLD20` (expired).

Sandbox payments: card `4111 1111 1111 1111` succeeds and `4000 0000 0000 0002` is declined. UPI IDs that start with `fail` are declined.

## Modules
| Module | Features |
|---|---|
| Authentication | Merchant/customer signup, sign in with email or mobile, BCrypt, JWT, role-based access |
| Restaurant | Profile, GST/tax %, active/accepting-orders status |
| Menu | Categories, items, prices, veg/non-veg, availability |
| Orders | Cart, bill preview, order placement, history, customer cancellation |
| Kitchen | Live board (Pending → Preparing → Ready), kitchen staff accounts |
| Billing & Payment | Automatic bill (subtotal − coupon − points + tax), signed sandbox gateway, counter cash, printable receipt, refunds on cancellation |
| Customers | Customer list with orders, spend and points |
| Coupons & Loyalty | Coupon validity, limits, minimum order; earn 1 pt per ₹100, redeem up to 50% |
| Notifications | Email (SMTP) + SMS (pluggable; simulated by default), consent-aware, logged |
| Feedback | Ratings and reviews for completed orders, complaints with merchant reply/status |
| Reports | Daily report, sales by date range, popular items, payment methods, rating summary |

## Main REST APIs
| Method | Endpoint | Purpose |
|---|---|---|
| POST | /api/auth/merchant/signup | Merchant registration |
| POST | /api/auth/customer/signup | Customer registration |
| POST | /api/auth/signin | Login |
| GET | /api/menu?restaurantId= | Fetch menu |
| POST | /api/orders/preview | Bill preview |
| POST | /api/orders | Create order |
| GET | /api/orders/{id} | Order details |
| PUT | /api/orders/{id}/status | Kitchen/merchant status update |
| POST | /api/orders/{id}/payments | Process/record payment |
| GET | /api/orders/{id}/receipt | Receipt |
| POST | /api/feedback | Submit feedback |
| GET | /api/reports/daily | Daily sales report |
| GET | /api/reports/sales?from=&to= | Sales report |
| * | /api/merchant/** | Menu, coupons, orders, customers, feedback, staff, profile |

## Configuration (environment variables)
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `PAYMENT_GATEWAY_SECRET`, `MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `SEED_DEMO_DATA`. Keep secrets out of the repository.
