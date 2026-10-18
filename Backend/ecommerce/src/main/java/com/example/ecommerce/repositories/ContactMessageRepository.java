package com.example.ecommerce.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.ContactMessage;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

    @Query(value = "select m from ContactMessage m where (:resolved is null or m.resolved = :resolved) order by m.createdAt desc",
            countQuery = "select count(m) from ContactMessage m where (:resolved is null or m.resolved = :resolved)")
    Page<ContactMessage> search(@Param("resolved") Boolean resolved, Pageable pageable);

    long countByResolvedFalse();
}
