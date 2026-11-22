/**
 * CS4065 Project 2 - Bulletin Board (Shifan Rashid, Ori Hernandez, and Kaden Sawyer)
 * Group: one message board and the users who have joined it.
 *
 * Part 1 uses a single group (id 0, "public"). Every public method is synchronized, so several ClientHandler
 * threads can use the same group safely: message ids never repeat, and each
 * member receives events in the same order they happened.
 */
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Group {
    private final int groupId;
    private final String groupName;

    /** members in the order they joined */
    private final Map<String, ClientConnection> membersByName = new LinkedHashMap<>();
    private final List<Message> messages = new ArrayList<>();
    private int nextMessageId = 1;

    public Group(int groupId, String groupName) {
        this.groupId = groupId;
        this.groupName = groupName;
    }

    public int getGroupId() { return groupId; }
    public String getGroupName() { return groupName; }

    public synchronized boolean isMember(String username) {
        return membersByName.containsKey(username);
    }

    /**
     * adds a user to the group. In one locked step this replies to the joiner
     * (users who joined earlier, then the last 2 posts) and tells every other
     * member, so no post or join can slip in between.
     */
    public synchronized void join(ClientConnection newMember) {
        String earlierMembers = String.join(",", membersByName.keySet());
        membersByName.put(newMember.getUsername(), newMember);

        newMember.send("OK|JOIN|" + groupId + "|" + earlierMembers);
        int firstRecentIndex = Math.max(0, messages.size() - 2);
        for (Message recent : messages.subList(firstRecentIndex, messages.size())) {
            newMember.send("RECENT|" + groupId + "|" + recent.toSummaryFields());
        }

        broadcast("JOINED|" + groupId + "|" + newMember.getUsername(), newMember.getUsername());
    }

    /** stores a new post, confirms it to the sender and notifies the other members */
    public synchronized void post(ClientConnection sender, String subject, String body) {
        Message newMessage = new Message(nextMessageId++, sender.getUsername(), subject, body);
        messages.add(newMessage);

        sender.send("OK|POST|" + groupId + "|" + newMessage.getMessageId());
        broadcast("NEW|" + groupId + "|" + newMessage.toSummaryFields(), sender.getUsername());
    }

    /** removes a user and tells the remaining members and returns false if they weren't a member */
    public synchronized boolean leave(String username) {
        if (membersByName.remove(username) == null) {
            return false;
        }
        broadcast("LEFT|" + groupId + "|" + username, null);
        return true;
    }

    /** all current members in join order */
    public synchronized String memberList() {
        return String.join(",", membersByName.keySet());
    }

    /** returns the message with this id, or null if there is none */
    public synchronized Message findMessage(int messageId) {
        for (Message message : messages) {
            if (message.getMessageId() == messageId) {
                return message;
            }
        }
        return null;
    }

    /** sends a line to every member except the one named and Caller holds the lock */
    private void broadcast(String line, String excludedUsername) {
        for (Map.Entry<String, ClientConnection> member : membersByName.entrySet()) {
            if (!member.getKey().equals(excludedUsername)) {
                member.getValue().send(line);
            }
        }
    }
}
