package com.example.student.service.impl;

import com.example.student.dto.user.UserRequest;
import com.example.student.dto.user.UserResponse;
import com.example.student.dto.user.UserResponseList;
import com.example.student.exception.EmailAlreadyExistsException;
import com.example.student.exception.EntityNotFoundException;
import com.example.student.mapper.UserMapper;
import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.example.student.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.MessageFormat;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public boolean register(UserRequest userRequest) {
        User user = userMapper.toEntity(userRequest);
        if (user == null || user.getEmail() == null) {
            return false;
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            return false;
        }
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userRepository.save(user);
        return true;
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(
                        ()->new EntityNotFoundException(
                                MessageFormat.format("User with email {0} not found!", email)));
    }

    @Transactional(readOnly = true)
    @Override
    public UserResponseList findAll() {
        return userMapper.toListResponse(userRepository.findAll());
    }

    @Transactional
    @Override
    public UserResponse save(UserRequest userRequest) {
        if (userRequest.getPassword() == null || userRequest.getPassword().isBlank()
                || userRequest.getPassword().length() < 6 || userRequest.getPassword().length() > 72) {
            throw new IllegalArgumentException("Пароль должен содержать от 6 до 72 символов");
        }
        if (userRepository.existsByEmail(userRequest.getEmail())) {
            throw new EmailAlreadyExistsException(
                    MessageFormat.format("Email {0} is already exists", userRequest.getEmail()));
        } else {
            User user = new User();
            user.setEmail(userRequest.getEmail());
            user.setPassword(passwordEncoder.encode(userRequest.getPassword()));
            user.setRoles(userRequest.getRoles());
            return userMapper.toResponse(userRepository.save(user));
        }
    }

    @Transactional
    @Override
    public UserResponse updateUser(Long id, UserRequest request) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));
        userRepository.findByEmail(request.getEmail()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new EmailAlreadyExistsException("Пользователь с таким email уже существует");
            }
        });
        existing.setEmail(request.getEmail());
        existing.setRoles(request.getRoles());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getPassword().length() < 6 || request.getPassword().length() > 72) {
                throw new IllegalArgumentException("Пароль должен содержать от 6 до 72 символов");
            }
            existing.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return userMapper.toResponse(userRepository.save(existing));
    }

    @Override
    public void deleteById(Long id) {
        userRepository.deleteById(id);
    }
}
