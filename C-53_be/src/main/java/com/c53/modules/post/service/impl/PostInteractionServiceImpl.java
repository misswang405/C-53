package com.c53.modules.post.service.impl;

import com.c53.modules.post.entity.PostEntity;
import com.c53.modules.post.repository.PostRepository;
import com.c53.modules.post.service.PostInteractionService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 动态互动数据访问适配实现。
 */
@Service
public class PostInteractionServiceImpl implements PostInteractionService {

    private final PostRepository postRepository;

    public PostInteractionServiceImpl(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Override
    public Optional<PostEntity> findPublished(String postId) {
        return postRepository.findById(postId)
                .filter(post -> "PUBLISHED".equals(post.getStatus()))
                .filter(post -> post.getPublishedAt() != null)
                .filter(post -> post.getDeletedAt() == null);
    }

    @Override
    public Map<String, PostEntity> findPublished(Collection<String> postIds) {
        return postRepository.findPublishedByIds(postIds).stream()
                .collect(Collectors.toMap(PostEntity::getId, Function.identity(), (a, b) -> a));
    }

    @Override
    public int incrementLike(String postId) {
        return postRepository.incrementLike(postId);
    }

    @Override
    public int decrementLike(String postId) {
        return postRepository.decrementLike(postId);
    }

    @Override
    public int incrementComment(String postId) {
        return postRepository.incrementComment(postId);
    }
}
