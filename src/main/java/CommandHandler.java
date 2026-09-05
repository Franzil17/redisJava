import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * CommandHandler — Parses incoming RESP commands and dispatches them to the
 * {@link DataStore}, writing RESP-encoded replies via {@link RespWriter}.
 *
 * <p>Supported commands:
 * <table border="1">
 *   <tr><th>Command</th><th>Response</th></tr>
 *   <tr><td>PING [message]</td><td>+PONG or bulk message</td></tr>
 *   <tr><td>SET key value</td><td>+OK</td></tr>
 *   <tr><td>GET key</td><td>bulk string or null bulk</td></tr>
 *   <tr><td>DEL key [key ...]</td><td>integer (count deleted)</td></tr>
 *   <tr><td>KEYS pattern</td><td>array of matching keys</td></tr>
 *   <tr><td>COMMAND / COMMAND DOCS</td><td>+OK (client handshake)</td></tr>
 * </table>
 *
 * <p>Any unknown command returns a RESP error response rather than crashing.
 */
public class CommandHandler {

    private final RespParser  parser;
    private final RespWriter  writer;
    private final DataStore   store;

    /**
     * Constructs a handler for one client connection.
     *
     * @param in  the client input stream
     * @param out the client output stream
     */
    public CommandHandler(InputStream in, OutputStream out) {
        this.parser = new RespParser(in);
        this.writer = new RespWriter(out);
        this.store  = DataStore.getInstance();
    }

    /**
     * Enters a read-dispatch loop for this client.
     * Blocks until the client disconnects or an unrecoverable I/O error occurs.
     */
    public void handleClient() {
        try {
            String[] tokens;
            while ((tokens = parser.readCommand()) != null) {
                dispatch(tokens);
            }
        } catch (IOException e) {
            System.err.println("[CommandHandler] Client disconnected or I/O error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Command dispatch
    // -------------------------------------------------------------------------

    private void dispatch(String[] tokens) throws IOException {
        if (tokens == null || tokens.length == 0) return;

        String command = tokens[0].toUpperCase();
        try {
            switch (command) {
                case "PING"    -> handlePing(tokens);
                case "SET"     -> handleSet(tokens);
                case "GET"     -> handleGet(tokens);
                case "DEL"     -> handleDel(tokens);
                case "KEYS"    -> handleKeys(tokens);
                case "COMMAND" -> writer.writeSimpleString("OK"); // Handshake compat
                default        -> writer.writeError("unknown command '" + command + "'");
            }
        } catch (IOException e) {
            throw e; // Propagate I/O errors — client likely disconnected
        } catch (Exception e) {
            // Guard against unexpected runtime errors so the loop keeps running
            try {
                writer.writeError("internal error: " + e.getMessage());
            } catch (IOException ignored) {}
            System.err.println("[CommandHandler] Unexpected error in command " + command + ": " + e);
        }
    }

    // -------------------------------------------------------------------------
    // Command handlers
    // -------------------------------------------------------------------------

    /** PING [message] → +PONG or $len\r\nmessage\r\n */
    private void handlePing(String[] tokens) throws IOException {
        if (tokens.length == 1) {
            writer.writeSimpleString("PONG");
        } else {
            writer.writeBulkString(tokens[1]);
        }
    }

    /** SET key value → +OK  |  -ERR wrong number of arguments */
    private void handleSet(String[] tokens) throws IOException {
        if (tokens.length < 3) {
            writer.writeError("wrong number of arguments for 'SET' command");
            return;
        }
        store.set(tokens[1], tokens[2]);
        System.out.printf("[SET] %s = %s%n", tokens[1], tokens[2]);
        writer.writeSimpleString("OK");
    }

    /** GET key → $len\r\nvalue\r\n  |  $-1\r\n (not found) */
    private void handleGet(String[] tokens) throws IOException {
        if (tokens.length < 2) {
            writer.writeError("wrong number of arguments for 'GET' command");
            return;
        }
        String value = store.get(tokens[1]).orElse(null);
        System.out.printf("[GET] %s → %s%n", tokens[1], value == null ? "(nil)" : value);
        writer.writeBulkString(value);
    }

    /** DEL key [key ...] → :count\r\n */
    private void handleDel(String[] tokens) throws IOException {
        if (tokens.length < 2) {
            writer.writeError("wrong number of arguments for 'DEL' command");
            return;
        }
        String[] keysToDelete = Arrays.copyOfRange(tokens, 1, tokens.length);
        int deleted = store.delete(keysToDelete);
        System.out.printf("[DEL] %s → %d deleted%n", Arrays.toString(keysToDelete), deleted);
        writer.writeInteger(deleted);
    }

    /** KEYS pattern → *N\r\n... */
    private void handleKeys(String[] tokens) throws IOException {
        if (tokens.length < 2) {
            writer.writeError("wrong number of arguments for 'KEYS' command");
            return;
        }
        List<String> matches = store.keys(tokens[1]);
        System.out.printf("[KEYS] pattern='%s' → %d match(es)%n", tokens[1], matches.size());
        writer.writeArray(matches);
    }
}
