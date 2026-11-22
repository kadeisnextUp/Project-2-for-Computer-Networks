/**
 * CS4065 Project 2 - Bulletin Board (Shifan Rashid, Ori Hernandez, and Kaden Sawyer)
 * Message: one post on a message board. Immutable once created.
 */
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Message {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final int messageId;
    private final String senderName;
    private final String postDate;
    private final String subject;
    private final String body;

    /** creates a message stamped with the server's current date and time */
    public Message(int messageId, String senderName, String subject, String body) {
        this.messageId = messageId;
        this.senderName = senderName;
        this.postDate = LocalDateTime.now().format(DATE_FORMAT);
        this.subject = subject;
        this.body = body;
    }

    public int getMessageId() { return messageId; }
    public String getSenderName() { return senderName; }
    public String getPostDate() { return postDate; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }

    /**
     * the summary fields shared by RECENT and NEW lines:
     * msgId|sender|date|subject ("Message ID, Sender, Post Date, Subject")
     */
    public String toSummaryFields() {
        return messageId + "|" + senderName + "|" + postDate + "|" + subject;
    }

    /** summary fields plus the body, used by the MESSAGE reply */
    public String toFullFields() {
        return toSummaryFields() + "|" + body;
    }
}
