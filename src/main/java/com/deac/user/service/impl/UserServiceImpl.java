package com.deac.user.service.impl;

import com.deac.features.membership.persistence.entity.MembershipEntry;
import com.deac.mail.EmailService;
import com.deac.exception.MyException;
import com.deac.security.jwt.refreshtoken.RefreshTokenProvider;
import com.deac.user.persistence.entity.Role;
import com.deac.user.persistence.entity.User;
import com.deac.user.persistence.repository.UserRepository;
import com.deac.security.jwt.accesstoken.AccessTokenProvider;
import com.deac.user.service.UserService;
import com.deac.user.token.entity.Token;
import com.deac.user.token.entity.TokenKey;
import com.deac.user.token.repository.TokenRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService, UserDetailsService {

    private final AuthenticationManager authenticationManager;

    private final PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper;

    private final UserRepository userRepository;

    private final TokenRepository tokenRepository;

    private final AccessTokenProvider accessTokenProvider;

    private final RefreshTokenProvider refreshTokenProvider;

    private final EmailService emailService;

    @Autowired
    public UserServiceImpl(AuthenticationManager authenticationManager,
                           PasswordEncoder passwordEncoder,
                           ObjectMapper objectMapper,
                           UserRepository userRepository,
                           TokenRepository tokenRepository,
                           AccessTokenProvider accessTokenProvider,
                           RefreshTokenProvider refreshTokenProvider,
                           EmailService emailService) {
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.accessTokenProvider = accessTokenProvider;
        this.refreshTokenProvider = refreshTokenProvider;
        this.emailService = emailService;
        if (!this.userRepository.existsByRoles(List.of(Role.ADMIN))) {
            User admin = new User("kyokushindev", "deackyokushindev@gmail.com", passwordEncoder.encode("=Zz]_e3v'uF-N(O"), "Admin", "", List.of(Role.ADMIN));
            admin.setVerified(true);
            admin.setEnabled(true);
            admin.setMembershipEntry(new MembershipEntry(true, Map.of(), true));
            this.userRepository.save(admin);
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            throw new UsernameNotFoundException("User does not exist");
        }
        User user = userOptional.get();
        if (!user.isVerified()) {
            throw new MyException("Email not verified yet", HttpStatus.UNAUTHORIZED);
        } else if (!user.isEnabled()) {
            throw new MyException("Account disabled", HttpStatus.UNAUTHORIZED);
        }
        return new org.springframework.security.core.userdetails.User(username, user.getPassword(), user.getRoles());
    }

    @Override
    public Map<String, String> signIn(String username, String password, String token) {
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            List<SimpleGrantedAuthority> roles = authentication.getAuthorities()
                    .stream().map(role -> new SimpleGrantedAuthority(role.toString())).collect(Collectors.toList());
            Date now = new Date();
            Date absoluteValidity = new Date(now.getTime() + refreshTokenProvider.getRefreshTokenAbsoluteValidityInMilliseconds());
            long loginIdentifier = now.getTime();
            String accessToken = accessTokenProvider.createToken(username, "access-token", roles);
            String refreshToken = refreshTokenProvider.createToken(token, username, "refresh-token", roles, loginIdentifier, absoluteValidity);
            return Map.of("accessToken", accessToken, "refreshToken", refreshToken, "authorities", objectMapper.writeValueAsString(roles));
        } catch (AuthenticationServiceException e) {
            throw e;
        } catch (AuthenticationException e) {
            throw new MyException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        } catch (JsonProcessingException e) {
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
            user.setRoles(List.of(Role.CLIENT));
            user.setEnabled(true);
            user.setMembershipEntry(new MembershipEntry());
            createCustomer(user);
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
        }
    }

    private void createCustomer(User user) {
        try {
            CustomerCreateParams customerCreateParams = CustomerCreateParams.builder()
                    .setName(user.getUsername())
                    .setEmail(user.getEmail())
                    .build();
            Customer customer = Customer.create(customerCreateParams);
            user.getMembershipEntry().setCustomerId(customer.getId());
        } catch (StripeException e) {
            throw new MyException("Unknown error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Map<String, String> refresh(String refreshToken) {
        try {
            String username = getCurrentUsername();
            Collection<? extends GrantedAuthority> roles = getCurrentAuthorities();
            Date absoluteValidity = refreshTokenProvider.getAbsoluteExpirationTimeFromToken(refreshToken);
            long loginIdentifier = refreshTokenProvider.getLoginIdentifierFromToken(refreshToken);
            String newAccessToken = accessTokenProvider.createToken(username, "access-token", roles);
            String newRefreshToken = refreshTokenProvider.updateToken(refreshToken, username, "refresh-token", roles, loginIdentifier, absoluteValidity);
            return Map.of("accessToken", newAccessToken, "refreshToken", newRefreshToken, "authorities", objectMapper.writeValueAsString(roles));
        } catch (Exception e) {
            throw new MyException("Could not refresh token", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String signOut() {
        try {
            refreshTokenProvider.invalidateUserTokens(getCurrentUsername());
            SecurityContextHolder.clearContext();
            return "Successfully logged out";
        } catch (Exception e) {
            throw new MyException("Could not log out", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }

    @Override
    public Collection<? extends GrantedAuthority> getCurrentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities();
    }

    @Override
    public Integer getCurrentUserId() {
        return userRepository.findByUsername(getCurrentUsername()).orElseThrow(() -> new MyException("User does not exist", HttpStatus.BAD_REQUEST)).getId();
    }

    @Override
    public String getUser(Integer userId) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            throw new MyException("User does not exist", HttpStatus.BAD_REQUEST);
        }
        return user.get().getUsername();
    }

    @Override
    public User getUserByUsername(String username) {
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            throw new MyException("User does not exist", HttpStatus.BAD_REQUEST);
        }
        return user.get();
    }

    @Override
    public User getCurrentUser() {
        Optional<User> user = userRepository.findByUsername(getCurrentUsername());
        if (user.isEmpty()) {
            throw new MyException("User does not exist", HttpStatus.BAD_REQUEST);
        }
        return user.get();
    }

    @Override
    public void setEnabled(String username, boolean isEnabled) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new MyException("User does not exist", HttpStatus.BAD_REQUEST));
        user.setEnabled(isEnabled);
        userRepository.save(user);
        if (!isEnabled) {
            refreshTokenProvider.invalidateUserTokens(username);
        }
    }

    @Override
    public void saveUser(User user) {
        userRepository.save(user);
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
                    "<h3>You've issued a request to reset your password. In order to do that, please follow this link: </h3><br>http://localhost:4200/reset-password?token=" + passwordToken);
            tokenRepository.save(new Token(new TokenKey(user.getId(), passwordTokenHash), expiresAt, "password-reset"));
            return "Recovery link sent if user exists";
        } catch (MessagingException e) {
            return "Recovery link sent if user exists";
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
            Integer userId = passwordToken.getTokenId().getUserId();
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
                tokenRepository.deleteByToken(tokenHash);
                throw new MyException("Password reset failed", HttpStatus.BAD_REQUEST);
            }
            if (!passwordToken.getPurpose().equals("password-reset")) {
                throw new MyException("Password reset failed", HttpStatus.BAD_REQUEST);
            }
            Long expiresAt = passwordToken.getExpiresAt();
            if (System.currentTimeMillis() > expiresAt) {
                tokenRepository.deleteByToken(tokenHash);
                throw new MyException("Reset token expired", HttpStatus.BAD_REQUEST);
            }
            User user = userOptional.get();
            user.setPassword(passwordEncoder.encode(password));
            userRepository.save(user);
            refreshTokenProvider.invalidateUserTokens(user.getUsername());
            tokenRepository.deleteAllByUserIdAndPurpose(userId, "password-reset");
            emailService.sendMessage(user.getEmail(),
                    "Your password has been changed",
                    "<h3>We've noticed that your password to your account has been changed. If this wasn't you, please contact our support immediately.");
            return "Password successfully reset";
        } catch (MessagingException e) {
            return "Password successfully reset";
        }
    }

    @Override
    public String sendUsernameReminderEmail(String email) {
        try {
            Optional<User> userOptional = userRepository.findByEmail(email);
            if (userOptional.isEmpty()) {
                return "Recovery email sent if user exists";
            }
            User user = userOptional.get();
            emailService.sendMessage(email,
                    "Username reminder",
                    "<h3>You've issued a request to get a reminder of your username.</h3><br><h3>Your username associated with this email is " + user.getUsername() + ".</h3>");
            return "Recovery email sent if user exists";
        } catch (MessagingException e) {
            return "Recovery email sent if user exists";
        }
    }

    @Override
    public String verifyEmail(String token) {
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
        user.setVerified(true);
        userRepository.save(user);
        tokenRepository.deleteAllByUserIdAndPurpose(userId, "verify-email");
        return "Email successfully verified";
    }

    @Override
    public void removeCookies(HttpServletResponse response) {
        refreshTokenProvider.removeCookies(response);
    }

    @Override
    public ResponseCookie setCookie(String name, String value, long age, boolean httpOnly, String path) {
        return refreshTokenProvider.setCookie(name, value, age, httpOnly, path);
    }

    @Scheduled(fixedDelay = 86400000)
    public void deleteExpiredRefreshTokens() {
        refreshTokenProvider.invalidateAllExpiredTokens();
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

}
