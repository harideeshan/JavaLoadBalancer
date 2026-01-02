import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class BackendServer {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Please provide a port number (e.g., 8081)");
            return;
        }
        int port = Integer.parseInt(args[0]);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            // --- ADD THIS TO SIMULATE HEAVY WORK ---
            try { Thread.sleep(5000); } catch (InterruptedException e) {} 
            // ---------------------------------------

            String response = "Hello! You have reached the server on port: " + port;
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
            System.out.println("Handled a request on port " + port);
        });

        System.out.println("Backend Server started on port " + port);
        server.start();
    }
}