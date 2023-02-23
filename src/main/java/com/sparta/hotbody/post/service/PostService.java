package com.sparta.hotbody.post.service;

import com.sparta.hotbody.post.dto.PostModifyRequestDto;
import com.sparta.hotbody.post.dto.PostRequestDto;
import com.sparta.hotbody.post.dto.PostResponseDto;
import com.sparta.hotbody.post.dto.PostSearchRequestDto;
import com.sparta.hotbody.post.entity.Post;
import com.sparta.hotbody.post.repository.PostRepository;
import com.sparta.hotbody.upload.entity.Image;
import com.sparta.hotbody.upload.repository.ImageRepository;
import com.sparta.hotbody.upload.service.UploadService;
import com.sparta.hotbody.user.entity.User;
import com.sparta.hotbody.user.service.UserDetailsImpl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PostService {

  private final PostRepository postRepository;
  private final UploadService uploadService;
  private final ImageRepository imageRepository;

  // 1. 게시글 등록
  @Transactional
  public Long createPost(PostRequestDto postRequestDto, UserDetailsImpl userDetails) {
    User user = userDetails.getUser();
    Post post = new Post(postRequestDto, user);
    Long id = postRepository.saveAndFlush(post).getId();

    return id;
  }

  @Transactional
  public ResponseEntity<String> createImage(MultipartFile file, Long id)
      throws IOException {
    Post post = postRepository.findById(id).get();
    if (post.getImage() != null) {
      Image image = imageRepository.findByResourcePath(post.getImage()).get();
      uploadService.remove(image.getResourcePath());
    }
    Image image = uploadService.storeFile(file);
    String resourcePath = image.getResourcePath();
    post.updateImage(resourcePath);

    return new ResponseEntity("사진이 등록되었습니다.", HttpStatus.OK);
  }



  // 2. 게시글 전체 조회
  public List<PostResponseDto> getAllPosts(int page, int size, String sortBy, boolean isAsc) {
    // 페이징 처리
    Sort.Direction direction = isAsc ? Sort.Direction.ASC : Sort.Direction.DESC;
    Sort sort = Sort.by(direction, sortBy);
    Pageable pageable = PageRequest.of(page, size, sort);

    Page<Post> posts = postRepository.findAll(pageable);
    List<PostResponseDto> postResponseDtoList = new ArrayList<>();

    for (Post post : posts) {
      PostResponseDto postResponseDto = new PostResponseDto(post.getNickname(), post.getTitle(),
          post.getContent(), post.getImage(), post.getLikes(), post.getCreatedAt(),
          post.getModifiedAt());
      postResponseDtoList.add(postResponseDto);
    }
    return postResponseDtoList;
  }

  // 3. 게시글 선택 조회
  @Transactional
  public PostResponseDto getPost(Long postId) {
    Post post = postRepository.findById(postId).orElseThrow(
        () -> new IllegalArgumentException("해당 게시글은 존재하지 않습니다.")
    );

    PostResponseDto postResponseDto = new PostResponseDto(post.getNickname(), post.getTitle(),
        post.getContent(), post.getImage(), post.getLikes(), post.getCreatedAt(),
        post.getModifiedAt());

    return postResponseDto;
  }

  // 키워드로 게시글 검색
  @Transactional
  public List<PostResponseDto> searchPost(PostSearchRequestDto postSearchRequestDto,
      int page, int size, String sortBy, boolean isAsc) {
    // 페이징 처리
    Sort.Direction direction = isAsc ? Sort.Direction.ASC : Sort.Direction.DESC;
    Sort sort = Sort.by(direction, sortBy);
    Pageable pageable = PageRequest.of(page, size, sort);

    List<PostResponseDto> postResponseDtoList = new ArrayList<>();

    if (postSearchRequestDto.getSearchType().equals("title")) {
      Page<Post> posts = postRepository.findByTitleContaining(
          postSearchRequestDto.getSearchKeyword(), pageable);

      for (Post post : posts) {
        PostResponseDto postResponseDto = new PostResponseDto(post.getNickname(), post.getTitle(),
            post.getContent(), post.getImage(), post.getLikes(), post.getCreatedAt(),
            post.getModifiedAt());
        postResponseDtoList.add(postResponseDto);
      }
    }

    if (postSearchRequestDto.getSearchType().equals("content")) {
      Page<Post> posts = postRepository.findByContentContaining(
          postSearchRequestDto.getSearchKeyword(), pageable);

      for (Post post : posts) {
        PostResponseDto postResponseDto = new PostResponseDto(post.getNickname(), post.getTitle(),
            post.getContent(), post.getImage(), post.getLikes(), post.getCreatedAt(),
            post.getModifiedAt());
        postResponseDtoList.add(postResponseDto);
      }
    }

    if (postSearchRequestDto.getSearchType().equals("nickname")) {
      Page<Post> posts = postRepository.findByNicknameContaining(
          postSearchRequestDto.getSearchKeyword(), pageable);

      for (Post post : posts) {
        PostResponseDto postResponseDto = new PostResponseDto(post.getNickname(), post.getTitle(),
            post.getContent(), post.getImage(), post.getLikes(), post.getCreatedAt(),
            post.getModifiedAt());
        postResponseDtoList.add(postResponseDto);
      }
    }
    return postResponseDtoList;
  }

  // 4. 게시글 수정
  @Transactional
  public void updatePost(Long postId, PostModifyRequestDto postModifyRequestDto,
      User user, MultipartFile file) throws IOException {
    Post post = postRepository.findById(postId).orElseThrow(
        () -> new IllegalArgumentException("해당 게시글은 존재하지 않습니다.")
    );

    if (post.getUser().getId().equals(user.getId())) {
      if (file != null) {
        Image image = uploadService.storeFile(file);
        uploadService.remove(post.getImage());
        post.modifyPost(postModifyRequestDto, image.getResourcePath());
        postRepository.save(post);
        return;
      }
      post.modifyPost(postModifyRequestDto);
      postRepository.save(post);
    } else {
      throw new IllegalArgumentException("게시글을 수정하려면 로그인이 필요합니다.");
    }
  }

  // 5. 게시글 삭제
  @Transactional
  public void deletePost(Long postId, User user) {
    Post post = postRepository.findById(postId).orElseThrow(
        () -> new IllegalArgumentException("해당 게시글은 존재하지 않습니다.")
    );
    if (post.getUser().getId().equals(user.getId())) {
      uploadService.remove(post.getImage());
      postRepository.delete(post);
    } else {
      throw new IllegalArgumentException("게시글을 삭제하려면 로그인이 필요합니다.");
    }
  }
}
