package com.deac.user.service.impl;

import com.deac.mail.EmailService;
import com.deac.exception.MyException;
import com.deac.user.persistence.entity.Role;
import com.deac.user.persistence.entity.User;
import com.deac.user.persistence.repository.UserRepository;
import com.deac.security.JwtTokenProvider;
import com.deac.user.service.UserService;
import com.deac.user.token.entity.Token;
import com.deac.user.token.entity.TokenKey;
import com.deac.user.token.repository.TokenRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService, UserDetailsService {

    private final UserRepository userRepository;

    private final TokenRepository tokenRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;

    private final JwtTokenProvider jwtTokenProvider;

    private final EmailService emailService;

    private final ObjectMapper objectMapper;

    @Autowired
    public UserServiceImpl(UserRepository userRepository,
                           TokenRepository tokenRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JwtTokenProvider jwtTokenProvider,
                           EmailService emailService,
                           ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        if (!this.userRepository.existsByRoles(List.of(Role.ADMIN))) {
            User admin = new User("kyokushindev", "deackyokushindev@gmail.com", passwordEncoder.encode("=Zz]_e3v'uF-N(O"), List.of(Role.ADMIN));
            admin.setEnabled(true);
            this.userRepository.save(admin);
        }
    }

    @Override
    public Map<String, String> signIn(String username, String password) {
        try {
            User user = userRepository.findByUsername(username);
            if (!user.isEnabled()) {
                throw new MyException("Email not verified yet", HttpStatus.UNAUTHORIZED);
            }
            List<Role> authorities = user.getRoles();
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password, authorities));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            Date absoluteValidity = new Date(new Date().getTime() + jwtTokenProvider.getRefreshTokenAbsoluteValidityInMilliseconds());
            String accessToken = jwtTokenProvider.createToken(username, user.getRoles(), "access-token", null);
            String refreshToken = jwtTokenProvider.createToken(username, user.getRoles(), "refresh-token", absoluteValidity);
            return Map.of("accessToken", accessToken, "refreshToken", refreshToken, "authorities", objectMapper.writeValueAsString(authorities));
        } catch (AuthenticationException e) {
            throw new MyException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        } catch (DataAccessException | JsonProcessingException e) {
            throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String signUp(User user) {
        try {
            if (userRepository.existsByUsername(user.getUsername()) || userRepository.existsByEmail(user.getEmail())) {
                throw new MyException("Username or email already exists", HttpStatus.CONFLICT);
            }
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            userRepository.save(user);
            String verifyToken = RandomStringUtils.randomAlphanumeric(64, 96);
            String verifyTokenHash = DigestUtils.sha256Hex(verifyToken);
            Long expiresAt = System.currentTimeMillis() + 604800000;
            tokenRepository.save(new Token(new TokenKey(user.getId(), verifyTokenHash), expiresAt, "verify-email"));
            emailService.sendMessage(user.getEmail(),
                    "Verify your email",
                    "<h3>Congratulations " + user.getUsername() + ", you have successfully registered to our website! In order to use our site, please verify your email here:</h3><br>http://localhost:4200/verify?token=" + verifyToken + "<br><h3>The link expires in 1 week, if you do not verify your email in the given time, we'll cancel your registration.<h3>");
            return "Successfully registered with user " + user.getUsername();
        } catch (MessagingException e) {
            userRepository.delete(user);
            throw new MyException("Could not send registration confirmation email", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (DataAccessException e) {
            throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Map<String, String> refresh(String refreshToken) {
        String username = validateRefreshToken(refreshToken);
        Date absoluteValidity = jwtTokenProvider.getAbsoluteExpirationTimeFromToken(refreshToken);
        String newAccessToken = jwtTokenProvider.createToken(username, userRepository.findByUsername(username).getRoles(), "access-token", null);
        String newRefreshToken = jwtTokenProvider.createToken(username, userRepository.findByUsername(username).getRoles(), "refresh-token", absoluteValidity);
        return Map.of("accessToken", newAccessToken, "refreshToken", newRefreshToken);
    }

    private String validateRefreshToken(String refreshToken) {
        try {
            jwtTokenProvider.validateToken(refreshToken);
        } catch (ExpiredJwtException e) {
            signOut();
            throw new MyException("Expired refresh token", HttpStatus.UNAUTHORIZED);
        } catch (JwtException | IllegalArgumentException e) {
            signOut();
            throw new MyException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }
        if (!jwtTokenProvider.getTypeFromToken(refreshToken).equals("refresh-token")) {
            signOut();
            throw new MyException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }
        return jwtTokenProvider.getUsernameFromToken(refreshToken);
    }

    @Override
    public String signOut() {
        SecurityContextHolder.clearContext();
        return "Successfully logged out";
    }

    @Override
    public Integer getCurrentUserId() {
        return userRepository.findByUsername(getCurrentUsername()).getId();
    }

    @Override
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }

    @Override
    public String getUser(Integer userId) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            throw new MyException("Given user does not exist", HttpStatus.BAD_REQUEST);
        }
        return user.get().getUsername();
    }

    @Override
    public String recoverPassword(String email) {
        try {
            Optional<User> userOptional = userRepository.findByEmail(email);
            if (userOptional.isEmpty()) {
                return "Recovery link sent if user exists";
            }
            User user = userOptional.get();
            if (tokenRepository.existsByUserId(user.getId())) {
                return "Recovery link sent if user exists";
            }
            String passwordToken = RandomStringUtils.randomAlphanumeric(64, 96);
            String passwordTokenHash = DigestUtils.sha256Hex(passwordToken);
            Long expiresAt = System.currentTimeMillis() + 300000;
            emailService.sendMessage(email,
                    "Reset your password",
                    "<h3>You've issued a request to reset your password. In order to do that, please follow this link: </h3><br>http://localhost:4200/reset?token=" + passwordToken);
            tokenRepository.save(new Token(new TokenKey(user.getId(), passwordTokenHash), expiresAt, "password-reset"));
            return "Recovery link sent if user exists";
        } catch (MessagingException e) {
            return "Recovery link sent if user exists";
        } catch (DataAccessException e) {
            throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String resetPassword(String token, String password) {
        try {
            String tokenHash = DigestUtils.sha256Hex(token);
            Optional<Token> tokenOptional = tokenRepository.findByToken(tokenHash);
            if (tokenOptional.isEmpty()) {
                throw new MyException("Password reset failed", HttpStatus.BAD_REQUEST);
            }
            Token passwordToken = tokenOptional.get();
            Long expiresAt = passwordToken.getExpiresAt();
            if (System.currentTimeMillis() > expiresAt) {
                tokenRepository.deleteByToken(tokenHash);
                throw new MyException("Reset token expired", HttpStatus.BAD_REQUEST);
            }
            Integer userId = passwordToken.getTokenId().getUserId();
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
                tokenRepository.deleteByToken(tokenHash);
                throw new MyException("Password reset failed", HttpStatus.BAD_REQUEST);
            }
            if (!passwordToken.getPurpose().equals("password-reset")) {
                throw new MyException("Password reset failed", HttpStatus.BAD_REQUEST);
            }
            User user = userOptional.get();
            user.setPassword(passwordEncoder.encode(password));
            userRepository.save(user);
            tokenRepository.deleteAllByUserIdAndPurpose(userId, "password-reset");
            emailService.sendMessage(user.getEmail(),
                    "Your password has been changed",
                    "<h3>We've noticed that your password to your account has been changed. If this wasn't you, please contact our support immediately.");
            return "Password successfully reset";
        } catch (MessagingException e) {
            return "Recovery link sent if user exists";
        } catch (DataAccessException e) {
            throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String verifyEmail(String token) {
        try {
            String tokenHash = DigestUtils.sha256Hex(token);
            Optional<Token> tokenOptional = tokenRepository.findByToken(tokenHash);
            if (tokenOptional.isEmpty()) {
                throw new MyException("Email verify failed", HttpStatus.BAD_REQUEST);
            }
            Token verifyToken = tokenOptional.get();
            Integer userId = verifyToken.getTokenId().getUserId();
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
                tokenRepository.deleteByToken(tokenHash);
                throw new MyException("Email verify failed", HttpStatus.BAD_REQUEST);
            }
            if (!verifyToken.getPurpose().equals("verify-email")) {
                throw new MyException("Email verify failed", HttpStatus.BAD_REQUEST);
            }
            Long expiresAt = verifyToken.getExpiresAt();
            if (System.currentTimeMillis() > expiresAt) {
                tokenRepository.deleteByToken(tokenHash);
                userRepository.deleteById(userId);
                throw new MyException("Verify token expired", HttpStatus.BAD_REQUEST);
            }
            User user = userOptional.get();
            user.setEnabled(true);
            userRepository.save(user);
            tokenRepository.deleteAllByUserIdAndPurpose(userId, "verify-email");
            return "Email successfully verified";
        } catch (DataAccessException e) {
            throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Scheduled(fixedDelay = 86400000)
    public void deleteExpiredPasswordTokens() {
        tokenRepository.deleteAllByExpiresAtBeforeAndPurpose(System.currentTimeMillis(), "password-reset");
    }

    @Scheduled(fixedDelay = 604800000)
    public void deleteExpiredVerifyTokens() {
        long time = System.currentTimeMillis();
        String purpose = "verify-email";
        List<Token> tokensToDelete = tokenRepository.findAllByExpiresAtBeforeAndPurpose(time, purpose);
        tokensToDelete.forEach(token -> {
            Integer userId = token.getTokenId().getUserId();
            userRepository.deleteById(userId);
        });
        tokenRepository.deleteAllByExpiresAtBeforeAndPurpose(time, purpose);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return new org.springframework.security.core.userdetails.User(username, user.getPassword(), user.getRoles());
    }

}
