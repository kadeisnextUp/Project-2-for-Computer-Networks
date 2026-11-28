/**
 * CS4065 Project 2 - Bulletin Board (Shifan Rashid, Ori Hernandez, and Kaden Sawyer)
 * BulletinClient: console client. Reads % commands, turns them into protocol
 * lines and sends them to the server.
 * A ServerListener thread prints everything the server sends back.
 *
 * Part 1 commands given by the assignment description: %connect, %join, %post, %users, 
 * %message, %leave, %exit, and  %help
 * Part 2 commands: %groups, %groupjoin, %grouppost, %groupusers, %groupleave, %groupmessage
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class BulletinClient {
    private static final String PUBLIC_GROUP = "0";
    private static final int REPLY_TIMEOUT_SECONDS = 5;
    private static final String VALID_USERNAME = "[^|,\\s]+";

    private final Scanner console = new Scanner(System.in);
    private final BlockingQueue<String> replyQueue = new LinkedBlockingQueue<>();
    private Socket socket;
    private PrintWriter toServer;

    public static void main(String[] args) {
        new BulletinClient().runCommandLoop();
    }

    private void runCommandLoop() {
        System.out.println("Bulletin board client. Type %help for the list of commands.");
        while (console.hasNextLine()) {
            String input = console.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }
            String[] parts = input.split("\\s+", 2);
            String command = parts[0].toLowerCase();
            String arguments = parts.length > 1 ? parts[1].trim() : "";

            if (command.equals("%exit")) {
                exit();
                return;
            }
            handleCommand(command, arguments);
        }
        exit();
    }

    private void handleCommand(String command, String arguments) {
        // given commands that don't need a connection
        if (command.equals("%help")) {
            printHelp();
            return;
        }
        if (command.equals("%connect")) {
            connect(arguments);
            return;
        }
        if (!isConnected()) {
            System.out.println("Not connected. Use %connect <host> <port> first.");
            return;
        }
        // given commands needing a connection
        switch (command) {
            case "%join":
                sendAndWait("JOIN|" + PUBLIC_GROUP);
                break;
            case "%post":
                post(arguments);
                break;
            case "%users":
                sendAndWait("USERS|" + PUBLIC_GROUP);
                break;
            case "%message":
                if (!arguments.matches("\\d+")) {
                    System.out.println("Usage: %message <message ID>");
                } else {
                    sendAndWait("MESSAGE|" + PUBLIC_GROUP + "|" + arguments);
                }
                break;
            case "%leave":
                sendAndWait("LEAVE|" + PUBLIC_GROUP);
                break;

            //For Part 2, we added the commands as well. 
            case "%groups":
                sendAndWait("GROUPS");
                break;
            case "%groupjoin":
                if (arguments.isEmpty()) {
                    System.out.println("Usage: %groupjoin <group id/name>");
                } else {
                    sendAndWait("JOIN|" + arguments);
                }
                break;
            case "%groupusers":
                 if (arguments.isEmpty()) {
                    System.out.println("Usage: %groupusers <group id/name>");
                } else {
                    sendAndWait("USERS|" + arguments);
                }
                break;
            case "%groupleave":
                if (arguments.isEmpty()) {
                    System.out.println("Usage: %groupleave <group id/name>");
                } else {
                    sendAndWait("LEAVE|" + arguments);
                }
                break;
            case "%groupmessage":
                groupMessage(arguments);
                break;
            case "%grouppost":
                groupPost(arguments);
                break;
            default:
                System.out.println("Unknown command. Type %help for the list of commands.");
        }
    }

    /** opens the socket, starts the listener, then registers a username. */
    private void connect(String arguments) {
        if (isConnected()) {
            System.out.println("Already connected. Use %exit to disconnect first.");
            return;
        }
        String[] hostAndPort = arguments.split("\\s+");
        if (hostAndPort.length != 2 || !hostAndPort[1].matches("\\d+")) {
            System.out.println("Usage: %connect <host> <port>   e.g. %connect localhost 5000");
            return;
        }
        try {
            socket = new Socket(hostAndPort[0], Integer.parseInt(hostAndPort[1]));
            toServer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            replyQueue.clear();
            Thread listenerThread = new Thread(new ServerListener(fromServer, replyQueue));
            listenerThread.setDaemon(true);
            listenerThread.start();
        } catch (IOException e) {
            System.out.println("Could not connect to " + arguments + ": " + e.getMessage());
            socket = null;
            return;
        }
        registerUsername();
    }

    /** asks for a username until the server accepts one that isn't taken. then asks for the group list so the user sees what can be joined */
    private void registerUsername() {
        while (isConnected()) {
            System.out.print("Enter a username: ");
            if (!console.hasNextLine()) {
                return;
            }
            String username = console.nextLine().trim();
            if (!username.matches(VALID_USERNAME)) {
                System.out.println("Usernames can't be empty or contain spaces, commas or '|'.");
                continue;
            }
            String reply = sendAndWait("USER|" + username);
            if (reply == null) {
                return;
            }
            if (reply.startsWith("OK|")) {
                sendAndWait("GROUPS"); // show the groups the user can join
                return;
            }
            
            
            // ERR|USER|NAME_TAKEN: the listener already printed why; ask again.
        }
    }
    

    /** %post <subject> | <body> */
    private void post(String arguments) {
        int separator = arguments.indexOf('|');
        if (separator < 0 || arguments.substring(0, separator).trim().isEmpty()) {
            System.out.println("Usage: %post <subject> | <message body>");
            return;
        }
        String subject = arguments.substring(0, separator).trim();
        String body = arguments.substring(separator + 1).trim();
        sendAndWait("POST|" + PUBLIC_GROUP + "|" + subject + "|" + body);
    }

   /** %groupmessage <group id/name> <message ID> */
    private void groupMessage(String arguments) {
        String[] parts = arguments.split("\\s+");
        if (parts.length != 2 || !parts[1].matches("\\d+")) {
            System.out.println("Usage: %groupmessage <group id/name> <message ID>");
            return;
        }
        String group = parts[0];
        String messageId = parts[1];
        sendAndWait("MESSAGE|" + group + "|" + messageId);
    }

    /** %grouppost <group id/name> <subject> | <body> */
    private void groupPost(String arguments) {
        String[] groupAndRest = arguments.split("\\s+", 2);
        if (groupAndRest.length < 2) {
            System.out.println("Usage: %grouppost <group id/name> <subject> | <message body>");
            return;
        }
        String group = groupAndRest[0];
        String subjectAndBody = groupAndRest[1];

        int separator = subjectAndBody.indexOf('|');
        if (separator < 0 || subjectAndBody.substring(0, separator).trim().isEmpty()) {
            System.out.println("Usage: %grouppost <group id/name> <subject> | <message body>");
            return;
        }
        String subject = subjectAndBody.substring(0, separator).trim();
        String body = subjectAndBody.substring(separator + 1).trim();
        sendAndWait("POST|" + group + "|" + subject + "|" + body);
    }  

    /** %exit: leaves every group on the server, closes the socket and quits. */
    private void exit() {
        if (isConnected()) {
            sendAndWait("EXIT");
            closeSocket();
        }
        System.out.println("Goodbye.");
    }

    /**
     * sends one request and waits for its OK/ERR reply (the listener prints it).
     * returns the reply line, or null if the server didn't answer.
     */
    private String sendAndWait(String requestLine) {
        toServer.println(requestLine);
        try {
            String reply = replyQueue.poll(REPLY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (reply == null) {
                System.out.println("No reply from the server.");
            } else if (reply.equals(ServerListener.CONNECTION_CLOSED)) {
                System.out.println("The server closed the connection.");
                closeSocket();
                return null;
            }
            return reply;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
        socket = null;
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  %connect <host> <port>      connect to a bulletin board server");
        System.out.println("  %join                       join the public message board");
        System.out.println("  %post <subject> | <body>    post a message (subject and body separated by |)");
        System.out.println("  %users                      list the users in the public board");
        System.out.println("  %message <message ID>       show the full content of a message");
        System.out.println("  %leave                      leave the public board");
        System.out.println("Groups (you can be in several at once):");
        System.out.println("  %groups                     list the groups you can join");
        System.out.println("  %groupjoin <group>          join a group by id or name");
        System.out.println("  %grouppost <group> <subject> | <body>   post a message to a group");
        System.out.println("  %groupusers <group>         list the users in a group");
        System.out.println("  %groupmessage <group> <ID>  show the full content of a message in a group");
        System.out.println("  %groupleave <group>         leave a group");
        System.out.println("  %exit                       disconnect and quit");
    }
}
