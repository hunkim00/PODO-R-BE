package be.podor.member.service;


import be.podor.member.dto.responsedto.KakaoUserInfoDto;
import be.podor.member.model.Member;
import be.podor.member.repository.MemberRepository;
import be.podor.security.UserDetailsImpl;
import be.podor.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class KakaoService {
    @Value("${kakao.client_id}")
    String kakaoClientId;
    @Value("${kakao.redirect_uri}")
    String RedirectURI;

    private final BCryptPasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;

    @Transactional
    public void kakaoLogin(String code, HttpServletResponse response)
            throws IOException {
        // 1. "인가코드" 로 "액세스 토큰" 요청
        String accessToken = getAccessToken(code);

        // 2. 토큰으로 카카오 API 호출
        KakaoUserInfoDto kakaoUserInfo = getKakaoUserInfo(accessToken);

        // 3. 카카오ID로 회원가입 처리
        Member kakaoUser = signupKakaoUser(kakaoUserInfo);

        //4. 강제 로그인 처리
        forceLoginKakaoUser(kakaoUser);

        // User 권한 확인
//        userRoleCheckService.userRoleCheck(kakaoUser);

        //  5. response Header에 JWT 토큰 추가
//        kakaoService.accessAndRefreshTokenProcess(kakaoUser.getNickname());

        new ResponseEntity<>(HttpStatus.OK);
    }

    //header 에 Content-type 지정
    //1번
    public String getAccessToken(String code) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        //HTTP Body 생성
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", kakaoClientId);
        body.add("redirect_uri", RedirectURI);
        body.add("code", code);

        //HTTP 요청 보내기
        HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest =
                new HttpEntity<>(body, headers);
        RestTemplate rt = new RestTemplate();
        ResponseEntity<String> response = rt.exchange(
                "https://kauth.kakao.com/oauth/token",
                HttpMethod.POST,
                kakaoTokenRequest,
                String.class
        );
        //HTTP 응답 (JSON) -> 액세스 토큰 파싱
        //JSON -> JsonNode 객체로 변환
        String responseBody = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        return jsonNode.get("access_token").asText();
    }

    //2번
    public KakaoUserInfoDto getKakaoUserInfo(String accessToken) throws IOException {
        // HTTP Header 생성
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
        // HTTP 요청 보내기
        HttpEntity<MultiValueMap<String, String>> kakaoUserInfoRequest = new HttpEntity<>(headers);
        RestTemplate rt = new RestTemplate();
        ResponseEntity<String> response = rt.exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.POST,
                kakaoUserInfoRequest,
                String.class
        );
        //HTTP 응답 (JSON)
        //JSON -> JsonNode 객체로 변환
        String responseBody = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        Long id = jsonNode.get("id").asLong();
        String nickname = jsonNode.get("properties")
                .get("nickname").asText();
        String profileUrl = jsonNode.get("properties")
                .get("profile_image").asText();
        return new KakaoUserInfoDto(id, nickname, profileUrl);
    }

    // 3번
    private Member signupKakaoUser(KakaoUserInfoDto kakaoUserInfoDto) {
        // 재가입 방지
//        int mannerTemp = userRoleCheckService.userResignCheck(kakaoUserInfoDto.getEmail());
        // DB 에 중복된 Kakao Id 가 있는지 확인
        Long kakaoId = kakaoUserInfoDto.getKakaoId();
        Member findKakao = memberRepository.findByKakaoId(kakaoUserInfoDto.getKakaoId())
                .orElse(null);

        //DB에 중복된 계정이 없으면 회원가입 처리
        if (findKakao == null) {
            String nickName = kakaoUserInfoDto.getNickname();
            String profilePic = kakaoUserInfoDto.getPropilePic();
            String password = UUID.randomUUID().toString();
            String encodedPassword = passwordEncoder.encode(password);
            LocalDateTime createdAt = LocalDateTime.now();
            Member kakaoUser = Member.builder()
                    .nickname(nickName)
                    .password(encodedPassword)
                    .profilePic(profilePic)
                    .createdAt(createdAt)
                    .kakaoId(kakaoId)
                    .build();
            memberRepository.save(kakaoUser);

            return kakaoUser;
        }
        return findKakao;
    }

    // 4번
    public void forceLoginKakaoUser(Member kakaoUser) {
        UserDetails userDetails = new UserDetailsImpl(kakaoUser);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }


//        public void accessAndRefreshTokenProcess(String username) {
//            String refreshToken = JwtTokenProvider.createRefreshToken();
//            JwtTokenProvider.createToken(username);
//        }
}
