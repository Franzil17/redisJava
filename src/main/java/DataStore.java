import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * DataStore — Thread-safe in-memory key-value store for the Redis agent.
 *
 * <p>Backed by a {@link ConcurrentHashMap}, so all operations are safe for
 * concurrent access from multiple client-handler threads.
 *
 * <p>Supports:
 * <ul>
 *   <li>{@link #set(String, String)}  — store a key/value pair</li>
 *   <li>{@link #get(String)}          — retrieve a value by key</li>
 *   <li>{@link #delete(String...)}    — delete one or more keys</li>
 *   <li>{@link #keys(String)}         — list keys matching a glob pattern</li>
 * </ul>
 */
public class DataStore {

    /** Singleton instance shared across all client-handler threads. */
    private static final DataStore INSTANCE = new DataStore();

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    private DataStore() {}

    /** Returns the shared singleton instance. */
    public static DataStore getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Core operations
    // -------------------------------------------------------------------------

    /**
     * Stores a key/value pair. Overwrites any existing value for the key.
     *
     * @param key   the key (must not be null)
     * @param value the value to store (must not be null)
     */
    public void set(String key, String value) {
        store.put(key, value);
    }

    /**
     * Retrieves the value associated with {@code key}.
     *
     * @param key the key to look up
     * @return an {@link Optional} containing the value, or empty if not found
     */
    public Optional<String> get(String key) {
        return Optional.ofNullable(store.get(key));
    }

    /**
     * Deletes one or more keys from the store.
     *
     * @param keys the keys to delete
     * @return the number of keys that actually existed and were removed
     */
    public int delete(String... keys) {
        int count = 0;
        for (String key : keys) {
            if (store.remove(key) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns a list of keys matching the given glob-style pattern.
     *
     * <p>Supported pattern characters:
     * <ul>
     *   <li>{@code *}  — matches any sequence of characters</li>
     *   <li>{@code ?}  — matches any single character</li>
     *   <li>{@code []} — character class (e.g. {@code [abc]})</li>
     * </ul>
     *
     * @param pattern a glob pattern (e.g. {@code "*"}, {@code "user:*"})
     * @return a list of matching key names (may be empty, never null)
     */
    public List<String> keys(String pattern) {
        Pattern regex = globToRegex(pattern);
        List<String> matches = new ArrayList<>();
        for (String key : store.keySet()) {
            if (regex.matcher(key).matches()) {
                matches.add(key);
            }
        }
        return matches;
    }

    /** Returns the current number of keys in the store. */
    public int size() {
        return store.size();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a Redis glob pattern to a Java {@link Pattern}.
     *
     * @param glob the glob pattern string
     * @return a compiled {@link Pattern}
     */
    private static Pattern globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '[' -> sb.append("[");
                case ']' -> sb.append("]");
                // Escape all other regex meta-characters
                case '.' , '(', ')', '{', '}', '+', '|', '^', '$', '\\' ->
                        sb.append("\\").append(c);
                default  -> sb.append(c);
            }
        }
        sb.append("$");
        try {
            return Pattern.compile(sb.toString());
        } catch (PatternSyntaxException e) {
            // Fallback: treat pattern as literal
            return Pattern.compile(Pattern.quote(glob));
        }
    }
}
