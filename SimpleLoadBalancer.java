import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleLoadBalancer {

    // Internal class to track Backend Server state
    static class Backend {
        String url;
        volatile boolean alive = true; // 'volatile' ensures visibility across threads

        Backend(String url) { this.url = url; }
    }

    private static final CopyOnWriteArrayList<Backend> backends = new CopyOnWriteArrayList<>();
    private static final AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {
        // Define our workers
        backends.add(new Backend("http://localhost:8081"));
        backends.add(new Backend("http://localhost:8082"));

        // 1. Start the Health Checker thread
        startHealthChecker();

        // 2. Start the Load Balancer Server
        HttpServer lb = HttpServer.create(new InetSocketAddress(8080), 0);
        
        lb.createContext("/", exchange -> {
            // Find the next available ALIVE server
            Backend target = getNextAliveServer();

            if (target == null) {
                String error = "503 Service Unavailable: No backends online";
                exchange.sendResponseHeaders(503, error.length());
                exchange.getResponseBody().write(error.getBytes());
                exchange.getResponseBody().close();
                return;
            }

            System.out.println("Routing to: " + target.url);
            
            try {
                // Forward the request and get response
                String response = proxyRequest(target.url);
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (Exception e) {
                exchange.sendResponseHeaders(502, 0); // Bad Gateway
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
        return null; // All servers are down
    }

    private static void startHealthChecker() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            for (Backend b : backends) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(b.url).openConnection();
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

    private static String proxyRequest(String targetUrl) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
        InputStream is = conn.getInputStream();
        return new String(is.readAllBytes());
    }
}