import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * RespWriter — Redis Serialization Protocol (RESP) response encoder.
 *
 * <p>Writes RESP-encoded responses to a client {@link OutputStream}. All
 * writes are flushed immediately so the client receives responses without delay.
 *
 * <p>Supported RESP types:
 * <ul>
 *   <li>Simple String: {@code +OK\r\n}</li>
 *   <li>Error:         {@code -ERR message\r\n}</li>
 *   <li>Integer:       {@code :42\r\n}</li>
 *   <li>Bulk String:   {@code $4\r\ndata\r\n}</li>
 *   <li>Null Bulk:     {@code $-1\r\n} (key not found)</li>
 *   <li>Array:         {@code *N\r\n...} (e.g. KEYS results)</li>
 * </ul>
 */
public class RespWriter {

    private final OutputStream out;

    /**
     * Constructs a writer that sends to the given stream.
     *
     * @param out the client output stream
     */
    public RespWriter(OutputStream out) {
        this.out = out;
    }

    /** Writes a RESP Simple String: {@code +message\r\n} */
    public void writeSimpleString(String message) throws IOException {
        write("+" + message + "\r\n");
    }

    /** Writes a RESP Error: {@code -ERR message\r\n} */
    public void writeError(String message) throws IOException {
        write("-ERR " + message + "\r\n");
    }

    /** Writes a RESP Integer: {@code :value\r\n} */
    public void writeInteger(long value) throws IOException {
        write(":" + value + "\r\n");
    }

    /**
     * Writes a RESP Bulk String: {@code $len\r\ndata\r\n}.
     * If {@code value} is {@code null}, writes a Null Bulk String {@code $-1\r\n}.
     */
    public void writeBulkString(String value) throws IOException {
        if (value == null) {
            write("$-1\r\n");
        } else {
            write("$" + value.length() + "\r\n" + value + "\r\n");
        }
    }

    /**
     * Writes a RESP Array of Bulk Strings: {@code *N\r\n$len\r\ndata\r\n...}.
     * If {@code items} is {@code null}, writes a Null Array {@code *-1\r\n}.
     */
    public void writeArray(List<String> items) throws IOException {
        if (items == null) {
            write("*-1\r\n");
            return;
        }
        write("*" + items.size() + "\r\n");
        for (String item : items) {
            writeBulkString(item);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void write(String data) throws IOException {
        out.write(data.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
