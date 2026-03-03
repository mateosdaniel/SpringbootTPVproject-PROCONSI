package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.DocumentType;
import com.proconsi.electrobazar.model.StoredDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoredDocumentRepository extends JpaRepository<StoredDocument, Long> {

    Optional<StoredDocument> findByDocumentTypeAndReferenceId(DocumentType documentType, Long referenceId);

    List<StoredDocument> findByReferenceId(Long referenceId);
}
