package com.selfstudy.backend.controller;

import com.selfstudy.backend.dto.ApiResponse;
import com.selfstudy.backend.model.Document;
import com.selfstudy.backend.service.DocumentService;
import com.selfstudy.backend.service.FileStorageService;
import com.selfstudy.backend.service.PdfExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private final FileStorageService fileStorageService;
    private final DocumentService documentService;
    private final PdfExtractionService pdfExtractionService;
    
    private static final List<String> ALLOWED_PDF_TYPES = Arrays.asList(
            "application/pdf"
    );
    
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Document>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description) {
        
        log.info("Received file upload request: {}, size: {}", file.getOriginalFilename(), file.getSize());
        
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_PDF_TYPES.contains(contentType)) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Only PDF files are allowed"));
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("File size exceeds the maximum limit of 100MB"));
        }
        
        if (file.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("File cannot be empty"));
        }
        
        try {
            String fileName = fileStorageService.storeFile(file);
            
            Document document = new Document();
            document.setTitle(title);
            document.setDescription(description);
            document.setFilePath(fileName);
            document.setFileType(contentType);
            document.setFileSize(file.getSize());
            document.setStatus(Document.ProcessingStatus.PENDING);
            
            Document savedDocument = documentService.createDocument(document);
            
            pdfExtractionService.extractTextFromPdf(savedDocument);
            
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("File uploaded successfully and processing started", savedDocument));
            
        } catch (Exception e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Could not upload the file: " + e.getMessage()));
        }
    }
}
