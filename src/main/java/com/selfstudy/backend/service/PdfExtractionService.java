package com.selfstudy.backend.service;

import com.selfstudy.backend.model.Document;
import com.selfstudy.backend.model.Topic;
import com.selfstudy.backend.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfExtractionService {

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final TopicExtractionService topicExtractionService;
    
    private static final int CHUNK_SIZE = 10; // Process 10 pages at a time

    @Async
    public CompletableFuture<String> extractTextFromPdf(Document document) {
        log.info("Starting text extraction for document: {}", document.getId());
        
        try {
            document.setStatus(Document.ProcessingStatus.PROCESSING);
            documentRepository.save(document);
            
            Path filePath = fileStorageService.getFilePath(document.getFilePath());
            
            try (PDDocument pdDocument = PDDocument.load(filePath.toFile())) {
                int pageCount = pdDocument.getNumberOfPages();
                document.setPageCount(pageCount);
                document.setExtractionProgress(0);
                documentRepository.save(document);
                
                log.info("Document {} has {} pages", document.getId(), pageCount);
            }
            
            StringBuilder fullText = new StringBuilder();
            int processedPages = 0;
            
            try (PDDocument pdDocument = PDDocument.load(filePath.toFile())) {
                int pageCount = pdDocument.getNumberOfPages();
                
                for (int i = 0; i < pageCount; i += CHUNK_SIZE) {
                    int endPage = Math.min(i + CHUNK_SIZE, pageCount);
                    String chunkText = extractTextFromPageRange(filePath, i, endPage);
                    fullText.append(chunkText);
                    
                    processedPages = endPage;
                    document.setExtractionProgress(processedPages);
                    documentRepository.save(document);
                    
                    log.info("Processed pages {}-{} of {} for document {}", 
                            i, endPage - 1, pageCount, document.getId());
                }
            }
            
            document.setExtractedText(fullText.toString());
            document.setStatus(Document.ProcessingStatus.COMPLETED);
            documentRepository.save(document);
            
            log.info("Completed text extraction for document: {}", document.getId());
            
            log.info("Starting topic extraction for document: {}", document.getId());
            CompletableFuture<List<Topic>> topicsFuture = topicExtractionService.extractTopicsFromDocument(document.getId());
            
            return CompletableFuture.completedFuture(fullText.toString());
            
        } catch (Exception e) {
            log.error("Error extracting text from PDF: {}", e.getMessage(), e);
            document.setStatus(Document.ProcessingStatus.FAILED);
            documentRepository.save(document);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private String extractTextFromPageRange(Path filePath, int startPage, int endPage) 
            throws IOException, TikaException, SAXException {
        
        try (InputStream inputStream = new FileInputStream(filePath.toFile())) {
            BodyContentHandler handler = new BodyContentHandler(-1); // -1 for unlimited text
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            PDFParser pdfParser = new PDFParser();
            
            pdfParser.parse(inputStream, handler, metadata, context);
            
            String fullText = handler.toString();
            
            List<String> pages = splitTextIntoPages(fullText);
            
            StringBuilder result = new StringBuilder();
            for (int i = startPage; i < endPage && i < pages.size(); i++) {
                result.append(pages.get(i)).append("\n\n");
            }
            
            return result.toString();
        }
    }
    
    private List<String> splitTextIntoPages(String text) {
        String[] paragraphs = text.split("\n\n");
        List<String> pages = new ArrayList<>();
        
        StringBuilder currentPage = new StringBuilder();
        int paragraphsPerPage = 5; // Approximate number of paragraphs per page
        
        for (int i = 0; i < paragraphs.length; i++) {
            currentPage.append(paragraphs[i]).append("\n\n");
            
            if ((i + 1) % paragraphsPerPage == 0) {
                pages.add(currentPage.toString());
                currentPage = new StringBuilder();
            }
        }
        
        if (currentPage.length() > 0) {
            pages.add(currentPage.toString());
        }
        
        return pages;
    }
}
