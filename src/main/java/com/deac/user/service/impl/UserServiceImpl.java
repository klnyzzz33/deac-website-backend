package com.deac.user.service.impl;

import com.deac.mail.EmailService;
import com.deac.exception.MyException;
import com.deac.user.persistence.entity.User;
import com.deac.user.persistence.repository.UserRepository;
import com.deac.security.JwtTokenProvider;
import com.deac.user.service.UserService;
import com.deac.user.token.entity.Token;
import com.deac.user.token.entity.TokenKey;
import com.deac.user.token.repository.TokenRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final TokenRepository tokenRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;

    private final JwtTokenProvider jwtTokenProvider;

    private final EmailService emailService;

    @Autowired
    public UserServiceImpl(UserRepository userRepository,
                           TokenRepository tokenRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JwtTokenProvider jwtTokenProvider,
                           EmailService emailService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailService = emailService;
        if (!this.userRepository.existsByRoles(List.of(User.Role.ROLE_ADMIN))) {
            User admin = new User("kyokushindev", "deackyokushindev@gmail.com", passwordEncoder.encode("=Zz]_e3v'uF-N(O"), List.of(User.Role.ROLE_ADMIN));
            admin.setEnabled(true);
            this.userRepository.save(admin);
        }
    }

    @Override
    public String signIn(String username, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            User user = userRepository.findByUsername(username);
            if (!user.isEnabled()) {
                throw new MyException("Email not verified yet", HttpStatus.UNAUTHORIZED);
            }
            return jwtTokenProvider.createToken(username, user.getRoles());
        } catch (AuthenticationException e) {
            throw new MyException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        } catch (DataAccessException e) {
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
            String verifyToken = RandomStringUtils.randomAlphanumeric(64);
            String verifyTokenHash = DigestUtils.sha256Hex(verifyToken);
            Long expiresAt = System.currentTimeMillis() + 604800000;
            tokenRepository.save(new Token(new TokenKey(user.getId(), verifyTokenHash), expiresAt, "verify-email"));
            emailService.sendMessage(user.getEmail(),
                    "Verify your email",
                    "<h3>Congratulations " + user.getUsername() + ", you have successfully registered to our website! In order to use our site, please verify your email here:</h3><br>http://localhost:4200/verify?token=" + verifyToken);
            return "Successfully registered with user " + user.getUsername();
        } catch (MessagingException e) {
            userRepository.delete(user);
            throw new MyException("Could not send registration confirmation email", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (DataAccessException e) {
            throw new MyException("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String verifyEmail(String token) {
        try {
            String tokenHash = DigestUtils.sha256Hex(token);
            if (!tokenRepository.existsByToken(tokenHash)) {
                throw new MyException("Email verify failed", HttpStatus.BAD_REQUEST);
            }
            Token verifyToken = tokenRepository.findByToken(tokenHash);
            Integer userId = verifyToken.getTokenId().getUserId();
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
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

    @Override
    public String refresh() {
        String username = getCurrentUsername();
        return jwtTokenProvider.createToken(username, userRepository.findByUsername(username).getRoles());
    }

    @Override
    public String signOut() {
        SecurityContextHolder.clearContext();
        return "Successfully logged out";
    }

    @Override
    public boolean hasAdminPrivileges() {
        String username = getCurrentUsername();
        return userRepository.findByUsername(username).getRoles().contains(User.Role.ROLE_ADMIN);
    }

    @Override
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.getName();
        }
        throw new MyException("You are not logged in", HttpStatus.UNAUTHORIZED);
    }

    @Override
    public Integer getCurrentUserId(String username) {
        return userRepository.findByUsername(username).getId();
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
            if (!userRepository.existsByEmail(email)) {
                return "Recovery link sent if user exists";
            }
            User user = userRepository.findByEmail(email);
            String passwordToken = RandomStringUtils.randomAlphanumeric(64);
            String passwordTokenHash = DigestUtils.sha256Hex(passwordToken);
            Long expiresAt = System.currentTimeMillis() + 300000;
            tokenRepository.save(new Token(new TokenKey(user.getId(), passwordTokenHash), expiresAt, "password-reset"));
            emailService.sendMessage(email,
                    "Reset your password",
                    "<h3>You've issued a request to reset your password. In order to do that, please follow this link: </h3><br>http://localhost:4200/reset?token=" + passwordToken);
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
            if (!tokenRepository.existsByToken(tokenHash)) {
                throw new MyException("Password reset failed", HttpStatus.BAD_REQUEST);
            }
            Token passwordToken = tokenRepository.findByToken(tokenHash);
            Long expiresAt = passwordToken.getExpiresAt();
            if (System.currentTimeMillis() > expiresAt) {
                tokenRepository.deleteByToken(tokenHash);
                throw new MyException("Reset token expired", HttpStatus.BAD_REQUEST);
            }
            Integer userId = passwordToken.getTokenId().getUserId();
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
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

    @Scheduled(fixedDelay = 300000)
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

}
