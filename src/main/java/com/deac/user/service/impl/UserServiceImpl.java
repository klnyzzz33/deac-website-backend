package com.deac.user.service.impl;

import com.deac.mail.EmailService;
import com.deac.user.exception.MyException;
import com.deac.user.persistence.entity.User;
import com.deac.user.persistence.repository.UserRepository;
import com.deac.user.security.TokenProvider;
import com.deac.user.service.UserService;
import com.deac.user.passwordtoken.entity.PasswordToken;
import com.deac.user.passwordtoken.entity.PasswordTokenKey;
import com.deac.user.passwordtoken.repository.PasswordTokenRepository;
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

    private final PasswordTokenRepository passwordTokenRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;

    private final TokenProvider tokenProvider;

    private final EmailService emailService;

    @Autowired
    public UserServiceImpl(UserRepository userRepository,
                           PasswordTokenRepository passwordTokenRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           TokenProvider tokenProvider,
                           EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordTokenRepository = passwordTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.emailService = emailService;
        if (!this.userRepository.existsByRoles(List.of(User.Role.ROLE_ADMIN))) {
            this.userRepository.save(new User("kyokushindev", "deackyokushindev@gmail.com", passwordEncoder.encode("=Zz]_e3v'uF-N(O"), List.of(User.Role.ROLE_ADMIN)));
        }
    }

    @Override
    public String signIn(String username, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return tokenProvider.createToken(username, userRepository.findByUsername(username).getRoles());
        } catch (AuthenticationException e) {
            System.out.println("asd");
            throw new MyException("Could not log in, invalid credentials", HttpStatus.UNAUTHORIZED);
        } catch (DataAccessException e) {
            throw new MyException("Could not log in, internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String signUp(User user) {
        try {
            if (userRepository.existsByUsername(user.getUsername()) || userRepository.existsByEmail(user.getEmail())) {
                throw new MyException("Could not register, username or email already exists", HttpStatus.CONFLICT);
            }
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            userRepository.save(user);
            emailService.sendMessage(user.getEmail(),
                    "Successful registration",
                    "<h3>Congratulations " + user.getUsername() + ", you have successfully registered to our website!</h3>");
            return "Successfully registered with user " + user.getUsername();
        } catch (MessagingException e) {
            userRepository.delete(user);
            throw new MyException("Could not send registration confirmation email", HttpStatus.BAD_REQUEST);
        } catch (DataAccessException e) {
            throw new MyException("Could not register, internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String refresh() {
        String username = getCurrentUsername();
        return tokenProvider.createToken(username, userRepository.findByUsername(username).getRoles());
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
            passwordTokenRepository.save(new PasswordToken(new PasswordTokenKey(user.getId(), passwordTokenHash), expiresAt));
            emailService.sendMessage(email,
                    "Reset your password",
                    "<h3>You've issued a request to reset your password. In order to do that, please follow this link: </h3><br>http://localhost:4200/reset?token=" + passwordToken);
            return "Recovery link sent if user exists";
        } catch (MessagingException e) {
            return "Recovery link sent if user exists";
        } catch (DataAccessException e) {
            throw new MyException("Could not reset password, internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String resetPassword(String token, String password) {
        try {
            String tokenHash = DigestUtils.sha256Hex(token);
            if (!passwordTokenRepository.existsByToken(tokenHash)) {
                throw new MyException("Password reset failed", HttpStatus.BAD_REQUEST);
            }
            PasswordToken passwordToken = passwordTokenRepository.findByToken(tokenHash);
            Long expiresAt = passwordToken.getExpiresAt();
            if (System.currentTimeMillis() > expiresAt) {
                passwordTokenRepository.deleteByToken(tokenHash);
                throw new MyException("Reset token expired", HttpStatus.BAD_REQUEST);
            }
            Integer userId = passwordToken.getTokenId().getUserId();
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
                throw new MyException("Password reset failed", HttpStatus.BAD_REQUEST);
            }
            User user = userOptional.get();
            user.setPassword(passwordEncoder.encode(password));
            userRepository.save(user);
            passwordTokenRepository.deleteAllByUserId(userId);
            return "Password successfully reset";
        } catch (DataAccessException e) {
            throw new MyException("Could not reset password, internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Scheduled(fixedDelay = 300000)
    public void deleteExpiredTokens() {
        passwordTokenRepository.deleteAllByExpiresAtBefore(System.currentTimeMillis());
    }

}
