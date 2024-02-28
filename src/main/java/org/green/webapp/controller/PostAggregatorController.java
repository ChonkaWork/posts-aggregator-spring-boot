package org.green.webapp.controller;

import org.green.webapp.model.Comment;
import org.green.webapp.model.Post;
import org.green.webapp.model.PostResult;
import org.green.webapp.model.User;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.counting;

@RequestMapping("/posts")
@RestController
public class PostAggregatorController {
    private static final String USERS_URL = "https://jsonplaceholder.typicode.com/users";
    private static final String POSTS_URL = "https://jsonplaceholder.typicode.com/posts";
    private static final String COMMENTS_URL = "https://jsonplaceholder.typicode.com/comments";

    private final RestTemplate restTemplate;

    public PostAggregatorController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping
    public ResponseEntity<List<PostResult>> aggregatePostsExecutorServiceCallable() throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(3);

        Callable<List<Post>> getPostsCallable = this::getPosts;
        Callable<List<User>> getUsersCallable = this::getUsers;
        Callable<List<Comment>> getCommentsCallable = this::getComments;

        Future<List<Post>> postsFuture = executor.submit(getPostsCallable);
        Future<List<User>> usersFuture = executor.submit(getUsersCallable);
        Future<List<Comment>> commentsFuture = executor.submit(getCommentsCallable);

        // Shutdown executor once all tasks are completed
        executor.shutdown();

        // Wait for all tasks to complete
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        List<Post> posts = postsFuture.get();
        List<User> users = usersFuture.get();
        List<Comment> comments = commentsFuture.get();

        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::id, Function.identity()));

        Map<Long, Long> postReviewsMap = comments.stream()
                .collect(Collectors.groupingBy(Comment::postId, Collectors.counting()));

        List<PostResult> postResults = posts.stream()
                .map(post -> new PostResult(post.id(),post.title(), userMap.get(post.userId()).name(), postReviewsMap.get(post.id())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(postResults);
    }

    @GetMapping("/alt")
    public ResponseEntity<List<PostResult>> aggregatePostsCompletableFuture() {
        CompletableFuture<List<Post>> postsFuture = CompletableFuture.supplyAsync(this::getPosts);
        CompletableFuture<List<User>> usersFuture = CompletableFuture.supplyAsync(this::getUsers);
        CompletableFuture<List<Comment>> commentsFuture = CompletableFuture.supplyAsync(this::getComments);

        // Wait for all CompletableFuture to complete
        CompletableFuture.allOf(postsFuture, usersFuture, commentsFuture).join();

        List<Post> posts = postsFuture.join();

        Map<Long, User> userMap =usersFuture.join()
                .stream()
                .collect(Collectors.toMap(User::id, Function.identity()));

        Map<Long, Long> postReviewsMap = commentsFuture.join()
                .stream()
                .collect(Collectors.groupingBy(Comment::postId, counting()));

        List<PostResult> postResults = posts.stream().map(getPostPostResult(userMap, postReviewsMap)).toList();

        return ResponseEntity.ok(postResults);
    }

    private static Function<Post, PostResult> getPostPostResult(Map<Long, User> userMap, Map<Long, Long> postReviewsMap) {
        return post -> {
            User author = userMap.get(post.userId());

            long reviewsCount = postReviewsMap.get(post.id());

            return new PostResult(post.id(), post.title(), author.name(), reviewsCount);
        };
    }

    private List<Comment> getComments() {
        ResponseEntity<List<Comment>> commentsEntity = restTemplate.exchange(COMMENTS_URL,
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                });
        return commentsEntity.getBody();
    }

    private List<User> getUsers() {
        ResponseEntity<List<User>> usersEntity = restTemplate.exchange(USERS_URL,
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                });
        return usersEntity.getBody();
    }

    private List<Post> getPosts() {
        ResponseEntity<List<Post>> posts = restTemplate.exchange(POSTS_URL,
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                });
        return posts.getBody();
    }

}
