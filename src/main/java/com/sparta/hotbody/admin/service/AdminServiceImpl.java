package com.sparta.hotbody.admin.service;

import com.sparta.hotbody.admin.dto.AdminSignUpRequestDto;
import com.sparta.hotbody.admin.dto.FindAdminIdRequestDto;
import com.sparta.hotbody.admin.dto.FindAdminIdResponseDto;
import com.sparta.hotbody.admin.dto.FindAdminPwRequestDto;
import com.sparta.hotbody.admin.dto.FindAdminPwResponseDto;
import com.sparta.hotbody.admin.entity.Admin;
import com.sparta.hotbody.admin.repository.AdminRepository;
import com.sparta.hotbody.comment.dto.CommentModifyRequestDto;
import com.sparta.hotbody.comment.entity.Comment;
import com.sparta.hotbody.comment.repository.CommentRepository;
import com.sparta.hotbody.common.jwt.JwtUtil;
import com.sparta.hotbody.common.jwt.entity.RefreshToken;
import com.sparta.hotbody.common.jwt.repository.RefreshTokenRepository;
import com.sparta.hotbody.common.page.PageDto;
import com.sparta.hotbody.post.dto.PostModifyRequestDto;
import com.sparta.hotbody.post.entity.Post;
import com.sparta.hotbody.post.repository.PostRepository;
import com.sparta.hotbody.user.dto.FindUserIdRequestDto;
import com.sparta.hotbody.user.dto.FindUserIdResponseDto;
import com.sparta.hotbody.user.dto.FindUserPwRequestDto;
import com.sparta.hotbody.user.dto.FindUserPwResponseDto;
import com.sparta.hotbody.user.dto.LoginRequestDto;
import com.sparta.hotbody.user.dto.TrainerResponseDto;
import com.sparta.hotbody.user.dto.UserProfileRequestDto;
import com.sparta.hotbody.user.dto.UserProfileResponseDto;
import com.sparta.hotbody.user.entity.Trainer;
import com.sparta.hotbody.user.entity.User;
import com.sparta.hotbody.user.entity.UserRole;
import com.sparta.hotbody.user.repository.PromoteRepository;
import com.sparta.hotbody.user.repository.UserRepository;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

  private static final String ADMIN_TOKEN = "D1d@A$5dm4&4D1d1i34n%7";
  private final UserRepository userRepository;
  private final PostRepository postRepository;
  private final CommentRepository commentRepository;
  private final PromoteRepository promoteRepository;
  private final AdminRepository adminRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final RefreshTokenRepository refreshTokenRepository;

  @Override
  public ResponseEntity signup(AdminSignUpRequestDto adminSignUpRequestDto) {
    String username = adminSignUpRequestDto.getUsername();
    String password = passwordEncoder.encode(adminSignUpRequestDto.getPassword());

    Optional<Admin> found = adminRepository.findByUsername(username);
    if (found.isPresent()) {
      throw new IllegalArgumentException("중복된 아이디가 존재합니다.");
    }
    if (!adminSignUpRequestDto.getAdminToken().equals(ADMIN_TOKEN)) {
      throw new IllegalArgumentException("관리자 암호가 틀렸습니다.");
    }
    UserRole role = UserRole.ADMIN;
    Admin admin = new Admin(adminSignUpRequestDto, password, role);
    adminRepository.save(admin);
    return new ResponseEntity("회원가입 완료", HttpStatus.OK);
  }

  @Override
  public ResponseEntity login(LoginRequestDto loginRequestDto, HttpServletResponse response)
      throws UnsupportedEncodingException {
    Admin admin = adminRepository.findByUsername(loginRequestDto.getUsername())
        .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다."));
    if (!passwordEncoder.matches(loginRequestDto.getPassword(), admin.getPassword())) {
      throw new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다.");
    }
    String accessToken = jwtUtil.createAccessToken(admin.getUsername(), admin.getRole());
    String refreshToken = jwtUtil.createRefreshToken(admin.getUsername(), admin.getRole());

    String encodedAccessToken = urlEncoder(accessToken);
    String encodedRefreshToken = urlEncoder(refreshToken);


    Cookie cookieAccessToken = new Cookie(jwtUtil.AUTHORIZATION_HEADER, encodedAccessToken);
    Cookie cookieRefreshToken = new Cookie(jwtUtil.REFRESH_TOKEN, encodedRefreshToken);

    response.addCookie(cookieAccessToken);
    response.addCookie(cookieRefreshToken);

    response.addHeader(JwtUtil.AUTHORIZATION_HEADER, accessToken);
    response.addHeader(JwtUtil.REFRESH_TOKEN, refreshToken);

    refreshTokenRepository.save(new RefreshToken(refreshToken.substring(7), admin));

    return new ResponseEntity("로그인 완료", HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity getRegistrations(PageDto pageDto) {
    Page<Trainer> trainerList = promoteRepository.findAll(pageDto.toPageable());
    if (trainerList.isEmpty()) {
      throw new IllegalArgumentException("페이지가 존재하지 않습니다.");
    }
    List<TrainerResponseDto> result = trainerList.stream()
        .map(m -> new TrainerResponseDto(m)).collect(Collectors.toList());
    return new ResponseEntity(result, HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity permitTrainer(Long requestId) {
    Trainer trainer = promoteRepository.findById(requestId)
        .orElseThrow(() -> new IllegalArgumentException("요청이 존재하지 않습니다."));
    trainer.getUser().TrainerPermission(trainer.getIntroduce());
    trainer.promote();
    return new ResponseEntity("트레이너 권한을 부여하였습니다.", HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity refuseTrainer(Long requestId) {
    Trainer trainer = promoteRepository.findById(requestId)
        .orElseThrow(() -> new IllegalArgumentException("요청이 존재하지 않습니다."));
    promoteRepository.delete(trainer);
    return new ResponseEntity("트레이너 요청이 삭제되었습니다.", HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity cancelTrainer(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("사용자가 존재하지 않습니다."));
    if (user.getRole().equals(UserRole.TRAINER)) {
      user.cancelPermission();
      if (promoteRepository.existsByUser(user)) {
        Trainer trainer = promoteRepository.findByUser(user);
        promoteRepository.delete(trainer);
      }
    } else {
      throw new IllegalArgumentException("해당 유저는 트레이너가 아닙니다.");
    }
    return new ResponseEntity("트레이너 권한을 삭제하였습니다.", HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity updatePost(Long postId, PostModifyRequestDto postModifyRequestDto) {
    Post post = postRepository.findById(postId).orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다."));
    post.modifyPost(postModifyRequestDto);
    return new ResponseEntity("게시글 수정을 완료하였습니다.", HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity deletePost(Long postId) {
    Post post = postRepository.findById(postId).orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다."));
    postRepository.delete(post);
    return new ResponseEntity("게시글 삭제가 완료되었습니다.", HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity updateComment(Long commentId, CommentModifyRequestDto commentModifyRequestDto) {
    Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new IllegalArgumentException("댓글이 존재하지 않습니다."));
    comment.modifyComment(commentModifyRequestDto);
    return new ResponseEntity("댓글 수정을 완료하였습니다.", HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity deleteComment(Long commentId) {
    Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new IllegalArgumentException("댓글이 존재하지 않습니다."));
    commentRepository.delete(comment);
    return new ResponseEntity("댓글 삭제가 완료되었습니다.", HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity getUserList(PageDto pageDto) {
    Page<Trainer> RequestList = promoteRepository.findAll(pageDto.toPageable());
    Page<User> userPage = userRepository.findAllByRole(UserRole.USER, pageDto.toPageable());
    if (userPage.isEmpty()) {
      throw new IllegalArgumentException("페이지가 존재하지 않습니다.");
    }
    Page<UserProfileResponseDto> userResponseDtoPage = new UserProfileResponseDto().toDtoPage(userPage);
    return new ResponseEntity(userResponseDtoPage, HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity getUser(Long userId) {
    User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("사용자가 존재하지 않습니다."));
    if (!user.getRole().equals(UserRole.USER)) {
      throw new IllegalArgumentException("유저가 아닙니다.");
    }
    return new ResponseEntity(new UserProfileResponseDto(user), HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity getTrainerList(PageDto pageDto) {
    Page<User> userPage = userRepository.findAllByRole(UserRole.TRAINER, pageDto.toPageable());
    if (userPage.isEmpty()) {
      throw new IllegalArgumentException("페이지가 존재하지 않습니다.");
    }
    Page<UserProfileResponseDto> userResponseDtoPage = new UserProfileResponseDto().toDtoPage(userPage);
    return new ResponseEntity(userResponseDtoPage, HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity getTrainer(Long trainerId) {
    User user = userRepository.findById(trainerId).orElseThrow(() -> new IllegalArgumentException("사용자가 존재하지 않습니다."));
    if (!user.getRole().equals(UserRole.TRAINER)) {
      throw new IllegalArgumentException("트레이너가 아닙니다.");
    }
    return new ResponseEntity(user, HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity updateUserInfo(Long userId, UserProfileRequestDto userRequestDto) {
    User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("사용자가 존재하지 않습니다."));
    user.update(userRequestDto);
    return new ResponseEntity("사용자 정보 수정하였습니다.", HttpStatus.OK);
  }

  @Override
  @Transactional
  public ResponseEntity deleteUser(Long userId) {
    User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("사용자가 존재하지 않습니다."));
    userRepository.delete(user);
    return new ResponseEntity("사용자 계정를 삭제하였습니다.", HttpStatus.OK);
  }

  // 관리자 아이디 찾기
  @Transactional
  public FindAdminIdResponseDto findAdminId(FindAdminIdRequestDto findAdminIdRequestDto) {
    Admin admin = adminRepository.findByEmail(findAdminIdRequestDto.getEmail()).orElseThrow(
        () -> new IllegalArgumentException("입력하신 이메일을 확인해 주세요.")
    );
    FindAdminIdResponseDto findAdminIdResponseDto = new FindAdminIdResponseDto(admin.getUsername());
    return findAdminIdResponseDto;
  }

  // 관리자 비밀번호 찾기
  @Transactional
  public FindAdminPwResponseDto findAdminPw(FindAdminPwRequestDto findAdminPwRequestDto) {
    Admin admin = adminRepository.findByUsernameAndEmail(findAdminPwRequestDto.getUsername(),
        findAdminPwRequestDto.getEmail()).orElseThrow(
        () -> new IllegalArgumentException("입력하신 아이디와 이메일을 확인해 주세요.")
    );
    // 임시 비밀번호 생성
    String password = generateTempPassword();
    FindAdminPwResponseDto findAdminPwResponseDto = new FindAdminPwResponseDto(password);

    // 비밀번호 encode 후 저장
    String encodePassword = passwordEncoder.encode(password);
    admin.modifyPassword(encodePassword);
    adminRepository.save(admin);

    return findAdminPwResponseDto;
  }

  // 쿠키에 저장하기 위한 인코더
  public String urlEncoder(String token) throws UnsupportedEncodingException {
    return URLEncoder.encode(token, "utf-8");
  }

  // 임시 비밀번호 생성
  public String generateTempPassword() {
    char[] charSet = new char[] {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
        'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
        'U', 'V', 'W', 'X', 'Y', 'Z',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
        'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
        'u', 'v', 'w', 'x', 'y', 'z'
    };
    SecureRandom secureRandom = new SecureRandom();
    secureRandom.setSeed(new Date().getTime());

    StringBuffer stringBuffer= new StringBuffer();

    int index = 0;
    int length = charSet.length;

    for (int i = 0; i < 20; i++) {
      index = secureRandom.nextInt(length);
      stringBuffer.append(charSet[index]);
    }

    return stringBuffer.toString();
  }
}
