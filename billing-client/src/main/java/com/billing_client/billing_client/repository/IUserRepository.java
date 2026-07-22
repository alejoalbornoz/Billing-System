package com.billing_client.billing_client.repository;

import com.billing_client.billing_client.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IUserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

}
