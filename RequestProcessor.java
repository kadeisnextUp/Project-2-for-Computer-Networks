/**
 * CS4065 Project 2 - Bulletin Board (Shifan Rashid, Ori Hernandez, and Kaden Sawyer)
 * RequestProcessor: turns one protocol line from a client into actions on the
 * board and the reply line(s). See the team's protocol spec for every format.
 *
 * How the server's ClientHandler uses it:
 *   - for each line read:            keepOpen = processor.handle(this, line);
 *   - when keepOpen is false:        close the socket (the client sent EXIT)
 *   - when the connection ends
 *     for any reason (finally):      processor.disconnect(this);
 */
public final class RequestProcessor {
    /** usernames can't be empty or contain '|', ',' or whitespace (they appear in lists). */
    private static final String VALID_USERNAME = "[^|,\\s]+";

    private final BoardRegistry registry;

    public RequestProcessor(BoardRegistry registry) {
        this.registry = registry;
    }

    /**
     * handles one request line and sends the reply to the client
     * returns false when the client asked to EXIT, true otherwise
     */
    public boolean handle(ClientConnection client, String line) {
        String requestType = line.split("\\|", 2)[0].trim().toUpperCase();

        if (!requestType.equals("USER") && client.getUsername() == null) {
            client.send("ERR|" + requestType + "|NOT_REGISTERED");
            return true;
        }

        switch (requestType) {
            case "USER":    handleUser(client, line);    return true;
            case "JOIN":    handleJoin(client, line);    return true;
            case "POST":    handlePost(client, line);    return true;
            case "USERS":   handleUsers(client, line);   return true;
            case "MESSAGE": handleMessage(client, line); return true;
            case "LEAVE":   handleLeave(client, line);   return true;
            case "EXIT":
                disconnect(client);
                client.send("OK|EXIT");
                return false;
            // Part 2: case "GROUPS"
            default:
                client.send("ERR|" + requestType + "|BAD_REQUEST");
                return true;
        }
    }

    /**
     * removes the client from every group and frees
     * its username and it can be called more than once
     */
    public void disconnect(ClientConnection client) {
        String username = client.getUsername();
        if (username == null) {
            return;
        }
        for (Group group : registry.allGroups()) {
            group.leave(username);
        }
        registry.releaseUsername(username);
        client.setUsername(null);
    }

    // USER|<username>
    private void handleUser(ClientConnection client, String line) {
        String[] fields = line.split("\\|", 2);
        if (fields.length != 2 || client.getUsername() != null
                || !fields[1].trim().matches(VALID_USERNAME)) {
            client.send("ERR|USER|BAD_REQUEST");
            return;
        }
        String requestedName = fields[1].trim();
        if (!registry.claimUsername(requestedName)) {
            client.send("ERR|USER|NAME_TAKEN");
            return;
        }
        client.setUsername(requestedName);
        client.send("OK|USER|" + requestedName);
    }

    // JOIN|<group>
    private void handleJoin(ClientConnection client, String line) {
        String[] fields = line.split("\\|", 2);
        if (fields.length != 2) {
            client.send("ERR|JOIN|BAD_REQUEST");
            return;
        }
        Group group = registry.findGroup(fields[1]);
        if (group == null) {
            client.send("ERR|JOIN|NO_SUCH_GROUP");
        } else if (group.isMember(client.getUsername())) {
            client.send("ERR|JOIN|ALREADY_IN_GROUP");
        } else {
            group.join(client); // sends OK|JOIN, the RECENT lines and JOINED
        }
    }

    // POST|<group>|<subject>|<body> 
    private void handlePost(ClientConnection client, String line) {
        String[] fields = line.split("\\|", 4);
        if (fields.length != 4 || fields[2].trim().isEmpty()) {
            client.send("ERR|POST|BAD_REQUEST");
            return;
        }
        Group group = findGroupAsMember(client, "POST", fields[1]);
        if (group != null) {
            group.post(client, fields[2].trim(), fields[3].trim()); // sends OK|POST and NEW
        }
    }

    // USERS|<group>
    private void handleUsers(ClientConnection client, String line) {
        String[] fields = line.split("\\|", 2);
        if (fields.length != 2) {
            client.send("ERR|USERS|BAD_REQUEST");
            return;
        }
        Group group = findGroupAsMember(client, "USERS", fields[1]);
        if (group != null) {
            client.send("OK|USERS|" + group.getGroupId() + "|" + group.memberList());
        }
    }

    // MESSAGE|<group>|<msgId>
    private void handleMessage(ClientConnection client, String line) {
        String[] fields = line.split("\\|", 3);
        if (fields.length != 3) {
            client.send("ERR|MESSAGE|BAD_REQUEST");
            return;
        }
        int messageId;
        try {
            messageId = Integer.parseInt(fields[2].trim());
        } catch (NumberFormatException notANumber) {
            client.send("ERR|MESSAGE|BAD_REQUEST");
            return;
        }
        Group group = findGroupAsMember(client, "MESSAGE", fields[1]);
        if (group == null) {
            return;
        }
        Message message = group.findMessage(messageId);
        if (message == null) {
            client.send("ERR|MESSAGE|NO_SUCH_MESSAGE");
        } else {
            client.send("OK|MESSAGE|" + group.getGroupId() + "|" + message.toFullFields());
        }
    }

    // LEAVE|<group>
    private void handleLeave(ClientConnection client, String line) {
        String[] fields = line.split("\\|", 2);
        if (fields.length != 2) {
            client.send("ERR|LEAVE|BAD_REQUEST");
            return;
        }
        Group group = findGroupAsMember(client, "LEAVE", fields[1]);
        if (group != null) {
            group.leave(client.getUsername()); // sends LEFT to the others
            client.send("OK|LEAVE|" + group.getGroupId());
        }
    }

    /**
     * looks up a group the client must already belong to and sends the matching
     * error and returns null when the group doesn't exist or they aren't in it
     */
    private Group findGroupAsMember(ClientConnection client, String requestType, String idOrName) {
        Group group = registry.findGroup(idOrName);
        if (group == null) {
            client.send("ERR|" + requestType + "|NO_SUCH_GROUP");
            return null;
        }
        if (!group.isMember(client.getUsername())) {
            client.send("ERR|" + requestType + "|NOT_IN_GROUP");
            return null;
        }
        return group;
    }
}
