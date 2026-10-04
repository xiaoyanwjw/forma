package com.xmut.forma.extension.tool.tech;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic markdown chunking for the tech-digest excerpt pipeline.
 */
public final class TechDigestSourcePrep {

    private static final int MIN_SEGMENT = 40;
    private static final int MIN_CHUNK = 800;
    private static final int MAX_CHUNK = 1500;
    private static final int OVERLAP = 80;
    private static final int MAX_CHUNKS = 12;

    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6}\\s+.+)$");

    private TechDigestSourcePrep() {
    }

    public static TechDigestPrepResult slice(String markdownOrText) {
        if (markdownOrText == null || markdownOrText.trim().isEmpty()) {
            return new TechDigestPrepResult(new ArrayList<TechDigestChunk>(), false);
        }
        List<SectionPiece> pieces = splitIntoPieces(markdownOrText);
        pieces = mergeShortPieces(pieces);
        List<TechDigestChunk> all = packChunks(pieces);
        if (all.size() <= MAX_CHUNKS) {
            return new TechDigestPrepResult(all, false);
        }
        return new TechDigestPrepResult(sampleTwelve(all), true);
    }

    private static List<SectionPiece> splitIntoPieces(String md) {
        List<SectionPiece> pieces = new ArrayList<SectionPiece>();
        int pos = 0;
        while (pos < md.length()) {
            int fence = indexOfFenceOpen(md, pos);
            if (fence < 0) {
                pieces.addAll(splitProse(md.substring(pos)));
                break;
            }
            if (fence > pos) {
                pieces.addAll(splitProse(md.substring(pos, fence)));
            }
            int close = indexOfFenceClose(md, fence);
            if (close < 0) {
                pieces.add(new SectionPiece("", md.substring(fence)));
                break;
            }
            int end = close;
            while (end < md.length() && md.charAt(end) != '\n') {
                end++;
            }
            if (end < md.length()) {
                end++;
            }
            pieces.add(new SectionPiece("", md.substring(fence, end)));
            pos = end;
        }
        return pieces;
    }

    private static int indexOfFenceOpen(String md, int from) {
        int i = md.indexOf("```", from);
        while (i >= 0) {
            if (i == 0 || md.charAt(i - 1) == '\n') {
                return i;
            }
            i = md.indexOf("```", i + 3);
        }
        return -1;
    }

    private static int indexOfFenceClose(String md, int open) {
        int i = md.indexOf("```", open + 3);
        while (i >= 0) {
            if (i == 0 || md.charAt(i - 1) == '\n') {
                return i;
            }
            i = md.indexOf("```", i + 3);
        }
        return -1;
    }

    private static List<SectionPiece> splitProse(String prose) {
        List<SectionPiece> pieces = new ArrayList<SectionPiece>();
        if (prose.isEmpty()) {
            return pieces;
        }
        String[] blocks = prose.split("\\n\\n+");
        for (String block : blocks) {
            if (block.trim().isEmpty()) {
                continue;
            }
            Matcher m = HEADING.matcher(block);
            if (m.find() && m.start() == 0) {
                String headingLine = m.group(1).trim();
                String heading = headingLine.replaceFirst("^#{1,6}\\s+", "").trim();
                String body = block.substring(m.end()).trim();
                if (body.isEmpty()) {
                    pieces.add(new SectionPiece(heading, headingLine));
                } else {
                    pieces.add(new SectionPiece(heading, block.trim()));
                }
            } else {
                pieces.add(new SectionPiece("", block.trim()));
            }
        }
        return pieces;
    }

    private static List<SectionPiece> mergeShortPieces(List<SectionPiece> pieces) {
        if (pieces.isEmpty()) {
            return pieces;
        }
        List<SectionPiece> merged = new ArrayList<SectionPiece>();
        SectionPiece current = pieces.get(0);
        for (int i = 1; i < pieces.size(); i++) {
            SectionPiece next = pieces.get(i);
            if (next.text.length() < MIN_SEGMENT && !next.text.contains("```")) {
                current = current.append(next);
            } else if (current.text.length() < MIN_SEGMENT && !current.text.contains("```")) {
                current = next.prepend(current);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private static List<TechDigestChunk> packChunks(List<SectionPiece> pieces) {
        List<TechDigestChunk> chunks = new ArrayList<TechDigestChunk>();
        if (pieces.isEmpty()) {
            return chunks;
        }
        String overlapPrefix = "";
        String heading = pieces.get(0).heading;
        StringBuilder buf = new StringBuilder();
        for (SectionPiece piece : pieces) {
            if (buf.length() == 0 && piece.heading.length() > 0) {
                heading = piece.heading;
            }
            if (buf.length() > 0) {
                buf.append("\n\n");
            }
            buf.append(piece.text);
            while (buf.length() >= MIN_CHUNK) {
                int take = Math.min(MAX_CHUNK - overlapPrefix.length(), buf.length());
                if (take <= 0) {
                    break;
                }
                String body = overlapPrefix + buf.substring(0, take);
                chunks.add(new TechDigestChunk(heading, body));
                String consumed = buf.substring(0, take);
                buf.delete(0, take);
                overlapPrefix = tail(consumed, OVERLAP);
                if (buf.length() > 0 && overlapPrefix.length() > 0) {
                    buf.insert(0, overlapPrefix);
                    overlapPrefix = "";
                }
            }
        }
        if (buf.length() > 0 || overlapPrefix.length() > 0) {
            String tail = overlapPrefix + buf.toString();
            if (tail.trim().length() > 0) {
                chunks.add(new TechDigestChunk(heading, tail));
            }
        }
        if (chunks.isEmpty()) {
            String combined = joinPieces(pieces);
            chunks.add(new TechDigestChunk(heading, combined));
        }
        return chunks;
    }

    private static String joinPieces(List<SectionPiece> pieces) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pieces.size(); i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append(pieces.get(i).text);
        }
        return sb.toString();
    }

    private static String tail(String s, int maxChars) {
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(s.length() - maxChars);
    }

    private static List<TechDigestChunk> sampleTwelve(List<TechDigestChunk> all) {
        int n = all.size();
        LinkedHashSet<Integer> indices = new LinkedHashSet<Integer>();
        for (int i = 0; i < 4 && i < n; i++) {
            indices.add(i);
        }
        int midStart = Math.max(4, (n - 4) / 2);
        for (int i = 0; i < 4 && midStart + i < n - 4; i++) {
            indices.add(midStart + i);
        }
        for (int i = Math.max(0, n - 4); i < n; i++) {
            indices.add(i);
        }
        List<TechDigestChunk> out = new ArrayList<TechDigestChunk>();
        for (Integer idx : indices) {
            if (out.size() >= MAX_CHUNKS) {
                break;
            }
            out.add(all.get(idx));
        }
        return out;
    }

    private static final class SectionPiece {
        private final String heading;
        private final String text;

        private SectionPiece(String heading, String text) {
            this.heading = heading == null ? "" : heading;
            this.text = text == null ? "" : text;
        }

        private SectionPiece append(SectionPiece other) {
            String h = heading.length() > 0 ? heading : other.heading;
            return new SectionPiece(h, text + "\n\n" + other.text);
        }

        private SectionPiece prepend(SectionPiece other) {
            String h = other.heading.length() > 0 ? other.heading : heading;
            return new SectionPiece(h, other.text + "\n\n" + text);
        }
    }
}
