package com.example.ecommerce.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.DeliveryPartnerProfile;
import com.example.ecommerce.enums.SellerStatus;

public interface DeliveryPartnerProfileRepository extends JpaRepository<DeliveryPartnerProfile, Long> {

    Optional<DeliveryPartnerProfile> findByUserId(Long userId);

    /** Admin list: optional status filter and a search over name, email, phone and city. */
    @Query(value = """
            select p from DeliveryPartnerProfile p join fetch p.user u
            where (:status is null or u.sellerStatus = :status)
              and (:term is null or lower(u.fullName) like lower(concat('%', :term, '%'))
                   or lower(u.email) like lower(concat('%', :term, '%'))
                   or u.phone like concat('%', :term, '%')
                   or lower(p.serviceCity) like lower(concat('%', :term, '%')))
            """,
            countQuery = """
            select count(p) from DeliveryPartnerProfile p join p.user u
            where (:status is null or u.sellerStatus = :status)
              and (:term is null or lower(u.fullName) like lower(concat('%', :term, '%'))
                   or lower(u.email) like lower(concat('%', :term, '%'))
                   or u.phone like concat('%', :term, '%')
                   or lower(p.serviceCity) like lower(concat('%', :term, '%')))
            """)
    Page<DeliveryPartnerProfile> search(@Param("status") SellerStatus status, @Param("term") String term, Pageable pageable);
}
