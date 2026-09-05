import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * RespParser — Redis Serialization Protocol (RESP) parser.
 *
 * <p>Reads RESP-encoded arrays from a client {@link InputStream} and returns
 * each command as a {@code String[]} of tokens. Supports:
 * <ul>
 *   <li>{@code *N\r\n}  — RESP array of N elements</li>
 *   <li>{@code $N\r\n}  — Bulk string of N bytes</li>
 * </ul>
 *
 * <p>Example wire input for "SET foo bar":
 * <pre>
 *   *3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n
 * </pre>
 */
public class RespParser {

    private final BufferedReader reader;

    /**
     * Constructs a parser that reads from the given stream.
     *
     * @param in the client input stream
     */
    public RespParser(InputStream in) {
        this.reader = new BufferedReader(new InputStreamReader(in));
    }

    /**
     * Reads and parses the next RESP command from the stream.
     *
     * @return a {@code String[]} of command tokens, or {@code null} if the
     *         client has disconnected (EOF).
     * @throws IOException if the input is malformed or an I/O error occurs.
     */
    public String[] readCommand() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null; // Client disconnected
        }

        if (!line.startsWith("*")) {
            // Inline command fallback (e.g. redis-cli raw PING)
            if (line.isBlank()) return readCommand();
            return line.trim().split("\\s+");
        }

        int numArgs;
        try {
            numArgs = Integer.parseInt(line.substring(1).trim());
        } catch (NumberFormatException e) {
            throw new IOException("Invalid RESP array header: " + line);
        }

        String[] tokens = new String[numArgs];
        for (int i = 0; i < numArgs; i++) {
            String lengthLine = reader.readLine();
            if (lengthLine == null || !lengthLine.startsWith("$")) {
                throw new IOException("Expected bulk string header, got: " + lengthLine);
            }
            int len;
            try {
                len = Integer.parseInt(lengthLine.substring(1).trim());
            } catch (NumberFormatException e) {
                throw new IOException("Invalid bulk string length: " + lengthLine);
            }

            // Read exactly len characters + the trailing \r\n
            char[] buf = new char[len];
            int read = 0;
            while (read < len) {
                int r = reader.read(buf, read, len - read);
                if (r == -1) throw new IOException("Unexpected EOF reading bulk string");
                read += r;
            }
            reader.readLine(); // consume trailing \r\n
            tokens[i] = new String(buf);
        }
        return tokens;
    }
}
