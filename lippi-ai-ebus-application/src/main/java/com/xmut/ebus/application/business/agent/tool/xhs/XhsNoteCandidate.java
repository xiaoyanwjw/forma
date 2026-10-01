package com.xmut.ebus.application.business.agent.tool.xhs;

/**
 * Internal XHS note candidate with a short id ({@code h1}…) for reranking.
 */
public final class XhsNoteCandidate {

    private final String id;
    private final String noteId;
    private final String title;
    private final String desc;
    private final String noteUrl;
    private final String likedCount;
    private final String author;
    private final String recallFrom;

    public XhsNoteCandidate(String id,
                            String noteId,
                            String title,
                            String desc,
                            String noteUrl,
                            String likedCount,
                            String author) {
        this(id, noteId, title, desc, noteUrl, likedCount, author, null);
    }

    public XhsNoteCandidate(String id,
                            String noteId,
                            String title,
                            String desc,
                            String noteUrl,
                            String likedCount,
                            String author,
                            String recallFrom) {
        this.id = id;
        this.noteId = noteId;
        this.title = title;
        this.desc = desc;
        this.noteUrl = noteUrl;
        this.likedCount = likedCount;
        this.author = author;
        this.recallFrom = recallFrom;
    }

    public String getId() {
        return id;
    }

    public String getNoteId() {
        return noteId;
    }

    public String getTitle() {
        return title;
    }

    public String getDesc() {
        return desc;
    }

    public String getNoteUrl() {
        return noteUrl;
    }

    public String getLikedCount() {
        return likedCount;
    }

    public String getAuthor() {
        return author;
    }

    public String getRecallFrom() {
        return recallFrom;
    }
}
