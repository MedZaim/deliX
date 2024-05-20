
/*
 * **
 *  * @project : DeliX
 *  * @created : 24/04/2024, 16:34
 *  * @modified : 24/04/2024, 16:34
 *  * @description : This file is part of the DeliX project.
 *  * @license : MIT License
 *  **
 */

package com.fsdm.pfe.delix.service.Impl;

import com.fsdm.pfe.delix.entity.PasswordResetToken;
import com.fsdm.pfe.delix.entity.User;
import com.fsdm.pfe.delix.entity.notification.Notification;
import com.fsdm.pfe.delix.exception.UserNotFoundException;
import com.fsdm.pfe.delix.exception.personalizedexceptions.NotificationNotFoundException;
import com.fsdm.pfe.delix.repository.PasswordResetTokenRepo;
import com.fsdm.pfe.delix.repository.UserRepo;

import com.fsdm.pfe.delix.repository.notification.NotificationRepo;
import com.fsdm.pfe.delix.service.UserService;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;


@Service
@Slf4j
public class UserServiceImpl implements UserService {
    private final PasswordEncoder passwordEncoder;
    private final UserRepo userRepository;
    private final NotificationRepo notificationRepository;
    private final PasswordResetTokenRepo passwordResetTokenRepository;

    private final EmailServiceImpl emailService;

    public UserServiceImpl(PasswordEncoder passwordEncoder, UserRepo userRepository, NotificationRepo notificationRepository, PasswordResetTokenRepo passwordResetTokenRepository, EmailServiceImpl emailService) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
    }

    @Override
    public User loadUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @Override
    public User saveUser(User user) {
        return userRepository.save(user);
    }


    @Override
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public boolean existsUserByEmail(String email) {
        return userRepository.existsUserByEmail(email);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    public Collection<User> loadAllUsers() {
        return userRepository.findAll();
    }


    public void updateUserEmail(Long userId, String newEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));

        user.setEmail(newEmail);
        userRepository.save(user);
    }

    public String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }


    public void sendEmailVerification(String email, String verificationToken, String baseUrl) {

        String verificationLink = baseUrl + "/verify?token=" + verificationToken;

        emailService.sendSimpleMessage(email, "Please verify your email",
                "Click the following link to verify your email: " + verificationLink);

    }

    public void sendPasswordResetEmail(String email, String resetToken, String baseUrl) {

        String resetLink = baseUrl + "/reset-password?token=" + resetToken;

        emailService.sendSimpleMessage(email, "Password reset",
                "Click the following link to reset your password: " + resetLink);

    }

    public void addNotificationToUser(Long userId, Notification notification) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));

        user.addNotification(notification);
        userRepository.save(user);
    }


    public void addNotificationToAllUsers(Notification notification) {
        List<User> users = userRepository.findAll();
        users.forEach(user -> user.addNotification(notification));
        userRepository.saveAll(users);
    }

    public void deleteNotificationFromUser(Long userId, Long notificationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException("Notification with id " + notificationId + " not found"));

        user.getNotifications().remove(notification);
        notificationRepository.delete(notification);
        userRepository.save(user);
    }

    public User loadUserByUsername(String username) {
        return userRepository.findByEmail(username).orElse(null);
    }

    public boolean passwordMatch(CharSequence rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }


    public PasswordResetToken createPasswordResetTokenForUser(User user) {
        String token = UUID.randomUUID().toString();

        passwordResetTokenRepository.deleteAllByUser(user);

        PasswordResetToken passwordResetToken = new PasswordResetToken(token, user);

        return passwordResetTokenRepository.save(passwordResetToken);
    }

    @Transactional
    public void resetPassword(String email, String baseUrl) {

        System.out.println("email = " + email);

        Optional<User> user = userRepository.findByEmail(email);
        System.out.println("user = " + user);
        if (user.isPresent()) {
            PasswordResetToken passwordResetToken = createPasswordResetTokenForUser(user.get());
            sendPasswordResetEmail(email, passwordResetToken.getToken(), baseUrl);
        } else {
            throw new UserNotFoundException("User with this email does not exist");
        }


    }

    public boolean existsUserByResetToken(String token) {
        return passwordResetTokenRepository.existsByToken(token);
    }

    @Transactional
    public User resetPasswordByToken(String token, String password, @NotNull @NotEmpty String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Token not found"));


        if (passwordResetToken.getExpiryDate().before(new Date())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token expired");
        }

        User user = passwordResetToken.getUser();

        user.setPassword(encodePassword(password));
        userRepository.save(user);
        passwordResetTokenRepository.delete(passwordResetToken);
        return user;
    }

}

