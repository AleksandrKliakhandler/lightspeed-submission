package com.akliakhandler.lightspeedsubmission;

import com.akliakhandler.lightspeedsubmission.model.Man;

import java.util.*;

public class Main {
    public static void main(String[] args) {
        List<String> fav = new ArrayList<>() {{
            add("Book1");
            add("Book2");
        }};

        Man originalMan = new Man("John", 30, fav);
        originalMan.setName("AnonJohn");
        originalMan.setAge(31);
        originalMan.setFavoriteBooks(new ArrayList<>(fav));

        Man clonedMan = DeepClone.deepClone(originalMan);

        if (originalMan == clonedMan) {
            throw new IllegalStateException();
        }

        if (!originalMan.getName().equals(clonedMan.getName())) {
            throw new IllegalStateException();
        }

        if (originalMan.getAge() != clonedMan.getAge()) {
            throw new IllegalStateException();
        }

        if (originalMan.getFavoriteBooks() == clonedMan.getFavoriteBooks()) {
            throw new IllegalStateException();
        }

        if (originalMan.getFavoriteBooks().size() != clonedMan.getFavoriteBooks().size()) {
            throw new IllegalStateException();
        }

        Map<String, Collection<?>> mixed = new HashMap<>();

        mixed.put("list", new ArrayList<>());
        mixed.put("linked", new LinkedList<>());
        mixed.put("set", new HashSet<>());
        mixed.put("lhset", new LinkedHashSet<>());
        mixed.put("treeSet", new TreeSet<>());
        mixed.put("deque", new ArrayDeque<>());
        mixed.put("pq", new PriorityQueue<>());

        Map<String,Object> container = new HashMap<>();
        container.put("man", originalMan);
        container.put("favorites", fav);
        container.put("mixed", mixed);

        Map<String,Object> cloneContainer = (Map<String,Object>) DeepClone.deepClone(container);

        if (container == cloneContainer) throw new IllegalStateException();
        if (cloneContainer.get("man") == container.get("man")) throw new IllegalStateException();
        if (cloneContainer.get("favorites") == container.get("favorites")) throw new IllegalStateException();
        if (cloneContainer.get("mixed") == container.get("mixed")) throw new IllegalStateException();

        Map<?,?> origMixed = (Map<?,?>) container.get("mixed");
        Map<?,?> clMixed = (Map<?,?>) cloneContainer.get("mixed");

        if (origMixed.size() != clMixed.size()) throw new IllegalStateException();

        Object cs1 = origMixed.get("treeSet");
        Object cs2 = clMixed.get("treeSet");
        if (cs1.getClass() != cs2.getClass()) throw new IllegalStateException();

        Object dq1 = origMixed.get("deque");
        Object dq2 = clMixed.get("deque");
        if (dq1.getClass() != dq2.getClass()) throw new IllegalStateException();

        System.out.println("OK");
    }

}
