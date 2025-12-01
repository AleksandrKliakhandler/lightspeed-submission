package com.akliakhandler.lightspeedsubmission.model;

import java.util.List;
import java.util.Objects;

public class Man extends Entity {
    private String name;
    private int age;
    private List<String> favoriteBooks;

    public Man(String name, int age, List<String> favoriteBooks) {
        super(name);
        this.age = age;
        this.favoriteBooks = favoriteBooks;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public List<String> getFavoriteBooks() {
        return favoriteBooks;
    }

    public void setFavoriteBooks(List<String> favoriteBooks) {
        this.favoriteBooks = favoriteBooks;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Man man = (Man) o;
        return age == man.age && Objects.equals(name, man.name) && Objects.equals(favoriteBooks, man.favoriteBooks);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(name);
        result = 31 * result + age;
        result = 31 * result + Objects.hashCode(favoriteBooks);
        return result;
    }
}