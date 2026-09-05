import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Main — Entry point for the Redis-compatible Java agent server.
 *
 * <p>Listens on port 6379 (the standard Redis port) and accepts multiple
 * concurrent clients. Each connection is handled on its own thread by a
 * {@link CommandHandler}, which speaks the Redis Serialization Protocol (RESP).
 *
 * <p>Supported commands: PING, SET, GET, DEL, KEYS
 *
 * <p>Compatible with any Redis client (redis-cli, Jedis, Lettuce, etc.) — simply
 * point the client at {@code localhost:6379} with no authentication required.
 */
public class Main {

    /** The TCP port the server listens on — same as real Redis. */
    private static final int PORT = 6379;

    public static void main(String[] args) {
        System.out.println("Redis Java Agent starting on port " + PORT + " ...");
        System.out.println("Supported commands: PING | SET | GET | DEL | KEYS");
        System.out.println("Connect with: redis-cli -h 127.0.0.1 -p " + PORT);
        System.out.println("----------------------------------------------------");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            // SO_REUSEADDR prevents 'Address already in use' on quick restarts
            serverSocket.setReuseAddress(true);

            System.out.println("[Server] Listening — waiting for clients...");

            // Accept loop: one thread per client, runs until the process is killed
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.printf("[Server] Client connected: %s%n",
                            clientSocket.getRemoteSocketAddress());

                    // Handle each client on its own daemon thread
                    Thread clientThread = new Thread(() -> {
                        try (Socket s = clientSocket) {
                            CommandHandler handler = new CommandHandler(
                                    s.getInputStream(),
                                    s.getOutputStream());
                            handler.handleClient();
                        } catch (IOException e) {
                            System.err.println("[Server] Client I/O error: " + e.getMessage());
                        } finally {
                            System.out.printf("[Server] Client disconnected: %s%n",
                                    clientSocket.getRemoteSocketAddress());
                        }
                    });
                    clientThread.setDaemon(true);
                    clientThread.setName("client-" + clientSocket.getPort());
                    clientThread.start();

                } catch (IOException e) {
                    System.err.println("[Server] Failed to accept connection: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            System.err.println("[Server] Fatal — could not bind to port " + PORT + ": " + e.getMessage());
            System.exit(1);
        }
    }
}

