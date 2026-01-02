import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleLoadBalancer {

    static class Backend {
        String url;
        volatile boolean alive = true;
        AtomicInteger activeConnections = new AtomicInteger(0); 

        Backend(String url) { this.url = url; }
    }

    private static final CopyOnWriteArrayList<Backend> backends = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws IOException {
        backends.add(new Backend("http://backend1:8081"));
        backends.add(new Backend("http://backend2:8082"));

        startHealthChecker();

        HttpServer lb = HttpServer.create(new InetSocketAddress(8080), 0);
        
        lb.createContext("/", exchange -> {
            // Initialize variables for this specific request
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            Backend target = null;

            // 1. STICKY SESSION LOGIC (With "Busy" Bypass)
            if (cookieHeader != null && cookieHeader.contains("SERVERID=")) {
                String savedPort = cookieHeader.split("SERVERID=")[1].split(";")[0];
                target = backends.stream()
                        .filter(b -> b.url.contains(savedPort) && b.alive)
                        .findFirst().orElse(null);
                
                // GOD TIER MOVE: If the server you're "stuck" to is busy, find a faster one!
                if (target != null && target.activeConnections.get() > 0) {
                    System.out.println("Sticky server " + target.url + " is busy (Load: " + target.activeConnections.get() + "). Rerouting for speed...");
                    target = null; // Reset so Least Connections takes over
                } else if (target != null) {
                    System.out.println("Sticky Session: Routing to " + target.url);
                }
            }

            // 2. LEAST CONNECTIONS LOGIC (Smart Load Balancing)
            if (target == null) {
                target = backends.stream()
                        .filter(b -> b.alive)
                        .min(Comparator.comparingInt(b -> b.activeConnections.get()))
                        .orElse(null);
                
                if(target != null) {
                    System.out.println("Least Connections: Routing to " + target.url + 
                                       " (Active Load: " + target.activeConnections.get() + ")");
                }
            }

            if (target == null) {
                String error = "503 Service Unavailable";
                exchange.sendResponseHeaders(503, error.length());
                exchange.getResponseBody().write(error.getBytes());
                exchange.close();
                return;
            }

            // Set the Cookie for the client
            String port = target.url.substring(target.url.lastIndexOf(":") + 1);
            exchange.getResponseHeaders().add("Set-Cookie", "SERVERID=" + port + "; Path=/");
            
            try {
                target.activeConnections.incrementAndGet(); // Track the start of work
                
                byte[] response = proxyRequest(target.url);
                exchange.sendResponseHeaders(200, response.length);
                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();
            } catch (Exception e) {
                exchange.sendResponseHeaders(502, 0);
                exchange.close();
            } finally {
                target.activeConnections.decrementAndGet(); // Track the end of work
            }
        });

        System.out.println("God Tier Load Balancer running on port 8080...");
        lb.start();
    }

    private static void startHealthChecker() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            for (Backend b : backends) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) URI.create(b.url).toURL().openConnection();
                    conn.setConnectTimeout(1000);
                    conn.connect();
                    b.alive = (conn.getResponseCode() == 200);
                } catch (Exception e) {
                    b.alive = false;
                }
                System.out.println("Check: " + b.url + " status: " + (b.alive ? "UP" : "DOWN") + 
                                   " | Current Load: " + b.activeConnections.get());
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private static byte[] proxyRequest(String targetUrl) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(targetUrl).toURL().openConnection();
        try (InputStream is = conn.getInputStream()) {
            return is.readAllBytes();
        }
    }
}