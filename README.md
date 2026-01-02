# High-Performance Layer 7 Java Load Balancer

A custom-built, asynchronous HTTP Load Balancer developed in Java. This project demonstrates core systems programming concepts including networking, concurrency, and fault tolerance.

## 🚀 Key Features
- **Layer 7 Content Switching**: Operates at the Application Layer, allowing for intelligent request routing.
- **Dynamic Health Checks**: A background monitoring system that automatically detects backend server failures and reroutes traffic to healthy nodes.
- **Sticky Sessions (Session Persistence)**: Implemented via custom Cookie injection to ensure stateful consistency for users.
- **Round-Robin Scheduling**: Distributed traffic management to ensure no single server is overwhelmed.
- **Fault Tolerance**: Zero-downtime routing; if a server goes down, the balancer self-heals in real-time.

## 🛠 Tech Stack
- **Language**: Java 21
- **Networking**: Java `HttpServer`, `HttpURLConnection`
- **Concurrency**: `ScheduledExecutorService`, `CopyOnWriteArrayList`, `AtomicInteger`

## 🏗 System Architecture
1. **Client** sends a request to the Load Balancer (Port 8080).
2. **Load Balancer** checks for a `SERVERID` cookie.
3. If no cookie exists, it selects an alive server using **Round-Robin**.
4. The Load Balancer proxies the request to the target **Backend Server** (8081/8082).
5. The Load Balancer injects a `Set-Cookie` header and returns the response to the Client.

## 🚦 How to Run
1. Start the backend workers:
   ```bash
   java BackendServer.java 8081
   java BackendServer.java 8082