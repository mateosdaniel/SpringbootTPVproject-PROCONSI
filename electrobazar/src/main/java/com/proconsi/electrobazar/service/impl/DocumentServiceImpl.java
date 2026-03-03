package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.DocumentType;
import com.proconsi.electrobazar.model.StoredDocument;
import com.proconsi.electrobazar.repository.StoredDocumentRepository;
import com.proconsi.electrobazar.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final StoredDocumentRepository storedDocumentRepository;

    @Override
    @Transactional
    public StoredDocument store(DocumentType type, Long referenceId, String filename, byte[] data) {
        log.info("Storing document type: {} for reference: {} with filename: {}", type, referenceId, filename);

        // Remove existing document of the same type and reference if it exists
        storedDocumentRepository.findByDocumentTypeAndReferenceId(type, referenceId)
                .ifPresent(existing -> {
                    log.info("Replacing existing document ID: {}", existing.getId());
                    storedDocumentRepository.delete(existing);
                });

        StoredDocument doc = StoredDocument.builder()
                .documentType(type)
                .referenceId(referenceId)
                .filename(filename)
                .data(data)
                .contentType("application/pdf")
                .createdAt(LocalDateTime.now())
                .build();

        return storedDocumentRepository.save(doc);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredDocument> findByTypeAndReference(DocumentType type, Long referenceId) {
        return storedDocumentRepository.findByDocumentTypeAndReferenceId(type, referenceId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredDocument> findByReference(Long referenceId) {
        return storedDocumentRepository.findByReferenceId(referenceId);
    }
}
