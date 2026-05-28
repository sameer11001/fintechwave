package com.fintechwave.iam.service;

import com.fintechwave.iam.domain.entity.User;

import java.util.UUID;

public interface IUserService {

    User findById(UUID id);

    User findByEmail(String email);

    boolean existsByEmail(String email);

    User save(User user);
}
