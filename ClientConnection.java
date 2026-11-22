/**
 * CS4065 Project 2 - Bulletin Board (Shifan Rashid, Ori Hernandez, and Kaden Sawyer)
 * ClientConnection: what the board logic needs from one connected client.
 *
 * The server's per-client thread (ClientHandler) implements this, so Group and
 * RequestProcessor never touch sockets directly.
 */
public interface ClientConnection {
    /** the accepted username, or null before USER succeeds. */
    String getUsername();

    void setUsername(String username);

    /** sends one protocol line to this client. Must be safe to call from any thread. */
    void send(String line);
}
