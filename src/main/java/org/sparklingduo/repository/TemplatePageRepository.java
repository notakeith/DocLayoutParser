package org.sparklingduo.repository;

import aj.org.objectweb.asm.commons.Remapper;
import org.sparklingduo.domain.template.TemplatePage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemplatePageRepository extends JpaRepository<TemplatePage, UUID> {

    @Query("SELECT p FROM TemplatePage p WHERE p.id IN " +
            "(SELECT p2.id FROM Template t JOIN t.pages p2 WHERE t.id = :templateId) " +
            "ORDER BY p.pageNumber ASC")
    List<TemplatePage> findByTemplateIdOrderByPageNumberAsc(@Param("templateId") UUID templateId);

    @Query("SELECT p FROM TemplatePage p WHERE p.id IN " +
            "(SELECT p2.id FROM Template t JOIN t.pages p2 WHERE t.id = :templateId) " +
            "AND p.pageNumber = :pageNumber")
    Optional<TemplatePage> findByTemplateIdAndPageNumber(
            @Param("templateId") UUID templateId,
            @Param("pageNumber") int pageNumber);
}