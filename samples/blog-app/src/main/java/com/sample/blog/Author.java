package com.sample.blog;

import jakarta.persistence.*;
import java.util.List;

@Entity
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "author")
    private List<Post> posts;

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<Post> getPosts() { return posts; }
}
