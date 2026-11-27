package com.example.aishopbot.repository;


import com.example.aishopbot.domain.UserState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStateRepository extends JpaRepository<UserState, Long> {
}