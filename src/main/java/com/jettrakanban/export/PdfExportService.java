package com.jettrakanban.export;

import com.jettrakanban.model.KanbanCard;
import com.jettrakanban.model.KanbanColumn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PdfExportService {
    private static final DateTimeFormatter DISPLAY_TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private PdfExportService() {
    }

    public static void exportBoard(Path target, String projectTitle, List<KanbanCard> cards) throws IOException {
        List<String> lines = buildLines(projectTitle, cards);
        writePdf(target, paginate(lines, 44));
    }

    private static List<String> buildLines(String projectTitle, List<KanbanCard> cards) {
        List<String> lines = new ArrayList<>();
        lines.add("JettraKanban");
        lines.add(projectTitle == null || projectTitle.isBlank() ? "Tablero" : projectTitle);
        lines.add("Exportado: " + LocalDateTime.now().format(DISPLAY_TS));
        lines.add("");

        Map<KanbanColumn, List<KanbanCard>> byColumn = new EnumMap<>(KanbanColumn.class);
        for (KanbanColumn column : KanbanColumn.values()) {
            byColumn.put(column, new ArrayList<>());
        }

        for (KanbanCard card : cards) {
            byColumn.get(card.column()).add(card);
        }

        for (KanbanColumn column : KanbanColumn.values()) {
            List<KanbanCard> columnCards = byColumn.get(column);
            if (columnCards.isEmpty()) {
                continue;
            }

            lines.add(column.displayName());
            columnCards.sort(Comparator.comparing(KanbanCard::title, String.CASE_INSENSITIVE_ORDER));
            for (KanbanCard card : columnCards) {
                lines.add("- " + safeText(card.title()));
                if (card.body() != null && !card.body().isBlank()) {
                    for (String bodyLine : wrapLines(safeText(card.body()), 88)) {
                        lines.add("  " + bodyLine);
                    }
                }
                String meta = buildMeta(card);
                if (!meta.isBlank()) {
                    lines.add("  " + meta);
                }
                lines.add("");
            }
        }

        if (lines.size() <= 4) {
            lines.add("No hay tarjetas para exportar.");
        }

        return lines;
    }

    private static String buildMeta(KanbanCard card) {
        List<String> parts = new ArrayList<>();
        if (card.createdBy() != null && !card.createdBy().isBlank()) {
            parts.add("Creado por: " + safeText(card.createdBy()));
        }
        if (card.createdAt() != null) {
            parts.add(card.createdAt().format(DISPLAY_TS));
        }
        return String.join("  ·  ", parts);
    }

    private static List<List<String>> paginate(List<String> lines, int linesPerPage) {
        List<List<String>> pages = new ArrayList<>();
        for (int index = 0; index < lines.size(); index += linesPerPage) {
            pages.add(new ArrayList<>(lines.subList(index, Math.min(lines.size(), index + linesPerPage))));
        }
        return pages;
    }

    private static void writePdf(Path target, List<List<String>> pages) throws IOException {
        List<byte[]> objects = new ArrayList<>();

        objects.add(object("<< /Type /Catalog /Pages 2 0 R >>"));

        StringBuilder kids = new StringBuilder();
        int pageCount = pages.size();
        int firstPageObject = 3;
        int fontObject = firstPageObject + pageCount * 2;

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            kids.append(firstPageObject + pageIndex * 2).append(" 0 R ");
        }
        objects.add(object("<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>"));

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            int pageObjectNumber = firstPageObject + pageIndex * 2;
            int contentObjectNumber = pageObjectNumber + 1;
            objects.add(object("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 " + fontObject + " 0 R >> >> /Contents " + contentObjectNumber + " 0 R >>"));
            objects.add(contentObject(pages.get(pageIndex)));
        }

        objects.add(object("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"));

        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");

        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);

        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n");
            pdf.append(new String(objects.get(i), StandardCharsets.ISO_8859_1));
            pdf.append("\nendobj\n");
        }

        int xrefPosition = pdf.length();
        pdf.append("xref\n");
        pdf.append("0 ").append(objects.size() + 1).append("\n");
        pdf.append("0000000000 65535 f \n");
        for (int offset : offsets.subList(1, offsets.size())) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
        pdf.append("startxref\n").append(xrefPosition).append("\n%%EOF");

        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, pdf.toString(), StandardCharsets.ISO_8859_1);
    }

    private static byte[] object(String body) {
        return body.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] contentObject(List<String> lines) {
        StringBuilder content = new StringBuilder();
        content.append("<< /Length ");
        String stream = buildStream(lines);
        content.append(stream.getBytes(StandardCharsets.ISO_8859_1).length).append(" >>\nstream\n");
        content.append(stream).append("\nendstream");
        return content.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String buildStream(List<String> lines) {
        StringBuilder stream = new StringBuilder();
        stream.append("BT\n/F1 12 Tf\n50 742 Td\n14 TL\n");
        for (String line : lines) {
            stream.append("(").append(escapePdfText(line)).append(") Tj\nT*\n");
        }
        stream.append("ET");
        return stream.toString();
    }

    private static String escapePdfText(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC);
        return normalized.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static List<String> wrapLines(String text, int maxCharacters) {
        List<String> wrapped = new ArrayList<>();
        for (String originalLine : text.split("\\R", -1)) {
            String line = originalLine.trim();
            if (line.isBlank()) {
                wrapped.add("");
                continue;
            }

            StringBuilder current = new StringBuilder();
            for (String word : line.split("\\s+")) {
                if (current.length() == 0) {
                    current.append(word);
                    continue;
                }

                if (current.length() + 1 + word.length() > maxCharacters) {
                    wrapped.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                } else {
                    current.append(' ').append(word);
                }
            }

            if (current.length() > 0) {
                wrapped.add(current.toString());
            }
        }
        return wrapped;
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }
}