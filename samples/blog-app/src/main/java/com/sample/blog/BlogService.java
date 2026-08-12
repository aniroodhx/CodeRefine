package com.sample.blog;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BlogService {

    private final AuthorRepository authorRepository;

    public BlogService(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    /**
     * CLASSIC N+1 BUG:
     * 1 query to load all authors, then 1 extra query per author to load
     * their posts (lazy). 100 authors => 101 queries.
     */
    public List<String> summarizeAllAuthors() {
        List<Author> authors = authorRepository.findAll();

        List<String> summaries = new ArrayList<>();
        for (Author author : authors) {
            int postCount = author.getPosts().size();   // <-- triggers N+1
            summaries.add(author.getName() + " has " + postCount + " posts");
        }
        return summaries;
    }
}
