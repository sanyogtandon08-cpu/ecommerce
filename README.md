# Fieldwork — Java storefront

A small, full stack outdoor goods shop built with Java's built in HTTP server and a plain HTML/CSS/JavaScript storefront. No framework or package manager is required.

## Run

Install JDK 17 or newer, then run `run.bat` on Windows or `./run.sh` on macOS/Linux. Open [http://localhost:8080](http://localhost:8080).

The server provides `GET /api/products`, `GET /api/orders`, and `POST /api/orders`. Orders are validated against server-side product prices and stock. The demo keeps orders in memory, so restarting the server resets them. Product photography loads from Unsplash and needs an internet connection.

## Layout

- `src/main/java/shop/ShopServer.java` — Java HTTP server and JSON API
- `src/main/resources/web/` — storefront UI
- `run.bat`, `run.sh` — compile and launch with the JDK
