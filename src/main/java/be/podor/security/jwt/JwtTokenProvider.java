package be.podor.security.jwt;

import be.podor.member.model.Member;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SecurityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@RequiredArgsConstructor
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secretKey}")
    private String secretKey;
    public static String BEARER_PREFIX = "Bearer";
    public final HttpServletResponse response;

    // 토큰 유효시간
    // 프론트엔드와 약속해야 함
    private final Long tokenValidTime = 30*60*1000L;  // 30분
    private final Long refreshTokenValidTime = 7*24*60*60*1000L;  // 1주일

    private final UserDetailsService userDetailsService;

    private final Key key;

    @PostConstruct
    protected void init() {
        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    // 토큰 생성
    public String createToken(Member member) {
        Date now = new Date();
        Claims claims = Jwts.claims().setSubject(member.getNickname());
        String Token = Jwts.builder()
                .setSubject(member.getNickname()) // 유저 정보 저장
                .setClaims(claims)  // 권한 정보 저장
                .setExpiration(new Date(now.getTime() + tokenValidTime)) // 만료 시간 정보
                .signWith(key, SignatureAlgorithm.HS256) // 키값과 알고리즘 세팅
                .compact();
        return BEARER_PREFIX+Token;
    }

    public String createRefreshToken() {
        Date now = new Date();
        String refreshToken= Jwts.builder()
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + refreshTokenValidTime))
                .signWith(key, SignatureAlgorithm.HS256) // 키값과 알고리즘 세팅
                .compact();
        response.addHeader("RefreshToken","Bearer " + refreshToken);
        return refreshToken;
    }

    // 토큰에서 회원 정보 추출
    public String getUserPk(String jwtToken) {
        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(jwtToken).getBody().getSubject();
    }

    // JWT 토큰에서 인증 정보 조회
    public Authentication getAuthentication(String jwtToken) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(this.getUserPk(jwtToken));
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    //Request의 Header에서 token 값을 가져옴
    //"X-AUTH-TOKEN":"TOKEN 깞"
    public String resolveToken(HttpServletRequest request) {
        return request.getHeader("Authorization");
    }
    public String resolveRefreshToken(HttpServletRequest request) {
        return request.getHeader("RefreshToken");
    }

    // 토큰 유효성 확인
    //    parserbuilder가 어떤 역할인지 알아내야겠음;;; 어려웡
    public boolean CheckToken(HttpServletRequest request) {
        String jwtToken = takeToken(request);
        try {
            Jwts
                    .parserBuilder()
//                    jwt 서명 검증을 위한 secret key를 들고온다.
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwtToken);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.info("Invalid JWT signature, 유효하지 않는 JWT 서명 입니다.");
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT token, 만료된 JWT token 입니다.");
        } catch (UnsupportedJwtException e) {
            log.info("Unsupported JWT token, 지원되지 않는 JWT 토큰 입니다.");
        } catch (IllegalArgumentException e) {
            log.info("JWT claims is empty, 잘못된 JWT 토큰 입니다.");
        }
        return false;
    }

    // 리퀘스트 헤더에서 토큰값가져오기
    public String takeToken(HttpServletRequest request) {
        return request.getHeader("Authorization").substring(7);
    }


}