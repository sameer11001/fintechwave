package com.fintechwave.iam.service.impl;

import com.fintechwave.iam.domain.entity.User;
import com.fintechwave.iam.exception.UserNotFoundException;
import com.fintechwave.iam.repository.UserRepository;
import com.fintechwave.iam.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserServiceImpl implements IUserService {

    private final UserRepository userRepository;

    @Override
    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public User save(User user) {
        User saved = userRepository.save(user);
        log.info("User saved: id={}", saved.getId());
        return saved;
    }
}
