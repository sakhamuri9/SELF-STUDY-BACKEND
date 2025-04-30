package com.selfstudy.backend.service;

import com.selfstudy.backend.dto.ApiResponse;
import com.selfstudy.backend.exception.ResourceNotFoundException;
import com.selfstudy.backend.model.Document;
import com.selfstudy.backend.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;

    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    public Document getDocumentById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", id));
    }

    public Document createDocument(Document document) {
        document.setStatus(Document.ProcessingStatus.PENDING);
        return documentRepository.save(document);
    }

    public Document updateDocument(Long id, Document documentDetails) {
        Document document = getDocumentById(id);
        
        document.setTitle(documentDetails.getTitle());
        document.setDescription(documentDetails.getDescription());
        document.setStatus(documentDetails.getStatus());
        
        return documentRepository.save(document);
    }

    public ApiResponse<Void> deleteDocument(Long id) {
        Document document = getDocumentById(id);
        documentRepository.delete(document);
        return ApiResponse.success("Document deleted successfully");
    }

    public List<Document> getDocumentsByStatus(Document.ProcessingStatus status) {
        return documentRepository.findByStatus(status);
    }
}
