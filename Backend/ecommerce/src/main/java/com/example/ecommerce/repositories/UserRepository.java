package com.example.ecommerce.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.SellerStatus;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    long countByRole(Role role);

    long countByRoleAndSellerStatus(Role role, SellerStatus sellerStatus);

    java.util.List<User> findByRoleAndActiveTrue(Role role);

    java.util.List<User> findTop5ByRoleAndSellerStatusOrderByCreatedAtDesc(Role role, SellerStatus sellerStatus);

    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:sellerStatus is null or u.sellerStatus = :sellerStatus)
              and (:search is null
                   or lower(u.fullName) like lower(concat('%', :search, '%'))
                   or lower(u.email) like lower(concat('%', :search, '%'))
                   or lower(u.storeName) like lower(concat('%', :search, '%')))
            """)
    Page<User> search(@Param("role") Role role,
                      @Param("sellerStatus") SellerStatus sellerStatus,
                      @Param("search") String search,
                      Pageable pageable);
}
