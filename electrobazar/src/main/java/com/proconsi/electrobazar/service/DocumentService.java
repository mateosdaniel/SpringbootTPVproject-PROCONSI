package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.DocumentType;
import com.proconsi.electrobazar.model.StoredDocument;

import java.util.List;
import java.util.Optional;

public interface DocumentService {

    StoredDocument store(DocumentType type, Long referenceId, String filename, byte[] data);

    Optional<StoredDocument> findByTypeAndReference(DocumentType type, Long referenceId);

    List<StoredDocument> findByReference(Long referenceId);
}
