package com.srihitaramanan.documentor.ingestion;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Extracts plain text from arbitrary document formats using Apache Tika.
 *
 * <p>Despite the name, this works for PDF, DOCX, TXT, HTML, RTF, ODT, etc.
 * Tika auto-detects the format.
 */
@Component
public class PdfTextExtractor {

    private final Tika tika = new Tika();

    public record ExtractedDocument(String text, int pageCount, String detectedMimeType) {}

    public ExtractedDocument extract(byte[] bytes, String filename) throws IngestionException {
        try {
            // Detect MIME type
            String detectedMime = tika.detect(bytes, filename);

            // Extract text (no character limit)
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);

            try (InputStream stream = new ByteArrayInputStream(bytes)) {
                AutoDetectParser parser = new AutoDetectParser();
                parser.parse(stream, handler, metadata, new ParseContext());
            }

            String text = handler.toString();
            if (text == null || text.isBlank()) {
                throw new IngestionException(
                        "No extractable text found in " + filename +
                                " (file may be image-only or empty)");
            }

            int pageCount = parsePageCount(metadata);
            return new ExtractedDocument(text, pageCount, detectedMime);

        } catch (IOException | SAXException | TikaException e) {
            throw new IngestionException("Failed to parse " + filename + ": " + e.getMessage(), e);
        }
    }

    private int parsePageCount(Metadata metadata) {
        String n = metadata.get("xmpTPg:NPages");
        if (n == null) n = metadata.get("Page-Count");
        if (n == null) return 1;
        try {
            return Integer.parseInt(n);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}