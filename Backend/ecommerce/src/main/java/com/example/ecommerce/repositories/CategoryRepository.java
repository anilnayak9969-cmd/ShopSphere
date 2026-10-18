package com.example.ecommerce.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.Category;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    boolean existsByCategoryNameIgnoreCase(String categoryName);

    boolean existsByCategoryNameIgnoreCaseAndCategoryIdNot(String categoryName, Integer categoryId);

    @EntityGraph(attributePaths = "parent")
    List<Category> findByStatusTrueOrderByCategoryNameAsc();

    long countByStatusTrue();

    long countByParentCategoryIdAndStatusTrue(Integer parentId);

    @Query("select c.categoryId, c.parent.categoryId from Category c")
    List<Object[]> parentLinks();

    @EntityGraph(attributePaths = "parent")
    @Query("""
            select c from Category c
            where (:search is null
                   or lower(c.categoryName) like lower(concat('%', :search, '%'))
                   or lower(c.description) like lower(concat('%', :search, '%')))
              and (:status is null or c.status = :status)
            """)
    Page<Category> search(@Param("search") String search, @Param("status") Boolean status, Pageable pageable);
}
