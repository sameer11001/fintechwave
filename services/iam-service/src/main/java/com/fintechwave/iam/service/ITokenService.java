package com.fintechwave.iam.service;

import com.fintechwave.iam.domain.entity.RefreshToken;
import com.fintechwave.iam.domain.entity.User;

public interface ITokenService {

    RefreshToken issue(User user);

    RefreshToken rotate(String incomingToken);

    void revoke(String token);

    void revokeAll(User user);
}
