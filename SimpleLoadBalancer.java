import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI; // Use URI to avoid deprecation warnings
import java.net.URL;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleLoadBalancer {

    static class Backend {
        String url;
        volatile boolean alive = true;
        Backend(String url) { this.url = url; }
    }

    private static final CopyOnWriteArrayList<Backend> backends = new CopyOnWriteArrayList<>();
    private static final AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {
        backends.add(new Backend("http://backend1:8081"));
        backends.add(new Backend("http://backend2:8082"));

        startHealthChecker();

        HttpServer lb = HttpServer.create(new InetSocketAddress(8080), 0);
        
        lb.createContext("/", exchange -> {
            // --- NEW: STICKY SESSION LOGIC ---
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            Backend target = null;

            if (cookieHeader != null && cookieHeader.contains("SERVERID=")) {
                String savedPort = cookieHeader.split("SERVERID=")[1].split(";")[0];
                target = backends.stream()
                        .filter(b -> b.url.contains(savedPort) && b.alive)
                        .findFirst().orElse(null);
                if(target != null) System.out.println("Sticky Session: Routing to " + target.url);
            }

            if (target == null) {
                target = getNextAliveServer();
                if(target != null) System.out.println("Round Robin: Routing to " + target.url);
            }
            // ---------------------------------

            if (target == null) {
                String error = "503 Service Unavailable";
                exchange.sendResponseHeaders(503, error.length());
                exchange.getResponseBody().write(error.getBytes());
                exchange.close();
                return;
            }

            // Set cookie so browser remembers this server
            String port = target.url.substring(target.url.lastIndexOf(":") + 1);
            exchange.getResponseHeaders().add("Set-Cookie", "SERVERID=" + port + "; Path=/");
            
            try {
                byte[] response = proxyRequest(target.url);
                exchange.sendResponseHeaders(200, response.length);
                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();
            } catch (Exception e) {
                exchange.sendResponseHeaders(502, 0);
                exchange.close();
            }
        });

        System.out.println("Load Balancer running on port 8080...");
        lb.start();
    }

    private static Backend getNextAliveServer() {
        for (int i = 0; i < backends.size(); i++) {
            int index = counter.getAndIncrement() % backends.size();
            Backend b = backends.get(index);
            if (b.alive) return b;
        }
        return null;
    }

    private static void startHealthChecker() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            for (Backend b : backends) {
                try {
                    // Modern way to create URL: URI.create().toURL()
                    HttpURLConnection conn = (HttpURLConnection) URI.create(b.url).toURL().openConnection();
                    conn.setConnectTimeout(1000);
                    conn.connect();
                    b.alive = (conn.getResponseCode() == 200);
                } catch (Exception e) {
                    b.alive = false;
                }
                System.out.println("Check: " + b.url + " status: " + (b.alive ? "UP" : "DOWN"));
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