/**
 * CS4065 Project 2 - Bulletin Board  (Shifan Rashid, Ori Hernandez, and Kaden Sawyer)
 * BoardRegistry: every group on the server, plus the set of usernames in use
 * one instance is shared by all ClientHandler threads
 * the public group's id is 0, private should be 1-5
 */
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.StringJoiner;

public final class BoardRegistry {
    public static final int PUBLIC_GROUP_ID = 0;

    /** groups sorted by id */
    private final Map<Integer, Group> groupsById = new ConcurrentSkipListMap<>();
    private final Set<String> usernamesInUse = ConcurrentHashMap.newKeySet();

    /** every server starts with the Part 1 public board (id 0) already created */
    public BoardRegistry() {
        addGroup(new Group(PUBLIC_GROUP_ID, "public"));
    /** 1.2 Addition of private groups NEW*/
        addGroup(new Group(1, "ComputerNetworks"));
        addGroup(new Group(2, "DataPrivacy"));
        addGroup(new Group(3, "OperatingSystems"));
        addGroup(new Group(4, "SoftwareEngineering"));
        addGroup(new Group(5, "RandomTopics"));
    }

    public void addGroup(Group group) {
        groupsById.put(group.getGroupId(), group);
    }

    /** finds a group by its id or name, return null if no match */
    public Group findGroup(String idOrName) {
        try {
            return groupsById.get(Integer.parseInt(idOrName.trim()));
        } catch (NumberFormatException notANumber) {
            for (Group group : groupsById.values()) {
                if (group.getGroupName().equalsIgnoreCase(idOrName.trim())) {
                    return group;
                }
            }
            return null;
        }
    }

    public Collection<Group> allGroups() {
        return groupsById.values();
    }

    /** 1.2 Group Listings, the group list the GROUPS reply sends to the client. NEW  */
     public String groupListing() {
         StringJoiner groupEntries = new StringJoiner(",");
         for (Group group : groupsById.values()){
             groupEntries.add(group.getGroupId() + ":" + group.getGroupName());
         }
         return groupEntries.toString();
     }

    /** reservse a username. Returns false if another client already has it */
    public boolean claimUsername(String username) {
        return usernamesInUse.add(username);
    }

    public void releaseUsername(String username) {
        usernamesInUse.remove(username);
    }
}
