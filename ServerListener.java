/**
 * CS4065 Project 2 - Bulletin Board (Shifan Rashid, Ori Hernandez, and Kaden Sawyer)
 * ServerListener: background thread that reads every line the server sends.
 *
 * notifications can arrive at any time, this one
 * thread prints everything from the server, in the order it arrives. Replies
 * are also handed to the main thread through a queue so it knows the outcome
 * of the command it just sent.
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerListener implements Runnable {
    /** put on the reply queue when the server closes the connection. */
    public static final String CONNECTION_CLOSED = "CLOSED";

    private final BufferedReader fromServer;
    private final BlockingQueue<String> replyQueue;
    /** true right after a JOIN reply, so the "recent posts" heading only prints if posts follow. */
    private boolean awaitingRecentPosts = false;
    //1.2 ADITTION
    private final Map<String, String> groupNames = new ConcurrentHashMap<>();

    public ServerListener(BufferedReader fromServer, BlockingQueue<String> replyQueue) {
        this.fromServer = fromServer;
        this.replyQueue = replyQueue;
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = fromServer.readLine()) != null) {
                printServerLine(line);
                if (line.startsWith("OK|") || line.startsWith("ERR|")) {
                    replyQueue.put(line);
                }
            }
            // close the socket 
        } catch (IOException | InterruptedException connectionEnded) {
        }
        replyQueue.offer(CONNECTION_CLOSED);
    }

    /** one protocol line for the user. */
    private void printServerLine(String line) {
        String[] fields = line.split("\\|", -1);
        if (fields[0].equals("RECENT") && awaitingRecentPosts) {
            System.out.println("Most recent posts (Message ID, Sender, Post Date, Subject):");
        }
        awaitingRecentPosts = false;
        switch (fields[0]) {
            case "OK":       printReply(line); break;
            case "ERR":      System.out.println("Error: " + describeError(fields[1], fields[2])); break;
            case "RECENT":   System.out.println("  " + formatSummary(line)); break;
            case "NEW":      System.out.println("[" + groupLabel(fields[1]) + "] New post: " + formatSummary(line)); break;
            case "JOINED":   System.out.println("[" + groupLabel(fields[1]) + "] " + fields[2] + " joined the group."); break;
            case "LEFT":     System.out.println("[" + groupLabel(fields[1]) + "] " + fields[2] + " left the group."); break;
            default:         System.out.println("Server: " + line);
        }
    }

    private void printReply(String line) {
        String[] fields = line.split("\\|", -1);
        switch (fields[1]) {
            case "USER":
                System.out.println("Connected as " + fields[2] + ". Type %join to join the public board.");
                break;
            case "JOIN":
                String earlier = fields[3].isEmpty() ? "none, you're the first one here" : fields[3].replace(",", ", ");
                System.out.println("Joined the " + groupLabel(fields[2]) + " board.");
                System.out.println("Users already in the group: " + earlier);
                awaitingRecentPosts = true;
                break;
            case "POST":
                System.out.println("Posted message #" + fields[3] + " to the " + groupLabel(fields[2]) + " board.");
                break;
            case "USERS":
                System.out.println("Users in the " + groupLabel(fields[2]) + " group: " + fields[3].replace(",", ", "));
                break;
            case "MESSAGE":
                // OK|MESSAGE|group|id|sender|date|subject|body (body may contain '|')
                String[] message = line.split("\\|", 8);
                System.out.println("Message #" + message[3] + " from " + message[4] + " on " + message[5]);
                System.out.println("Subject: " + message[6]);
                System.out.println(message[7]);
                break;
            case "LEAVE":
                System.out.println("You left the " + groupLabel(fields[2]) + " board.");
                break;
            case "GROUPS":
                //OK|GRUOPS| id:name
                System.out.println("Available groups (id: name):");
                System.out.println("  1: ComputerNetworks");
                System.out.println("  2: DataPrivacy");
                System.out.println("  3: OperatingSystems");
                System.out.println("  4: SoftwareEngineering");
                System.out.println("  5: RandomTopics");
            case "EXIT":
                System.out.println("Disconnected from the server.");
                break;
            default:
                System.out.println("Server: " + line);
        }
    }

    /** "TYPE|group|id|sender|date|subject" -> "#id, sender, date, subject" */
    private static String formatSummary(String line) {
        String[] fields = line.split("\\|", 6);
        return "#" + fields[2] + ", " + fields[3] + ", " + fields[4] + ", " + fields[5];
    }

    /** group 0 is the public board. Part 2 can map ids 1-5 to their names here. */
    private String groupLabel(String groupId) {
         switch (groupId) {
            case "0":  return "public";
            case "1":  return "ComputerNetworks (group 1)";
            case "2":  return "DataPrivacy (group 2)";
            case "3":  return "OperatingSystems (group 3)";
            case "4":  return "SoftwareEngineering (group 4)";
            case "5":  return "RandomTopics (group 5)";
            default:   return "group " + groupId;
        }
    }
        
        

    private static String describeError(String requestType, String code) {
        switch (code) {
            case "NAME_TAKEN":       return "that username is already taken.";
            case "NOT_REGISTERED":   return "choose a username first.";
            case "NO_SUCH_GROUP":    return "that group doesn't exist.";
            case "ALREADY_IN_GROUP": return "you're already in that group.";
            case "NOT_IN_GROUP":     return "join the group first (%join).";
            case "NO_SUCH_MESSAGE":  return "there's no message with that ID.";
            case "BAD_REQUEST":      return "the server didn't understand the " + requestType + " request.";
            default:                 return requestType + " failed (" + code + ").";
        }
    }
}
