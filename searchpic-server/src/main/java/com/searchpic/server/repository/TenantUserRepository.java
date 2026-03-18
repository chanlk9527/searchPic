package com.searchpic.server.repository;

import com.searchpic.server.model.entity.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {
    Optional<TenantUser> findByTenantIdAndUserId(String tenantId, String userId);
    List<TenantUser> findAllByTenantIdOrderByDisplayNameAsc(String tenantId);
}
