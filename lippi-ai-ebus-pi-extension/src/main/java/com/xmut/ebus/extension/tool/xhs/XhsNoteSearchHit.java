package com.xmut.ebus.extension.tool.xhs;

/**
 * Immutable Xiaohongshu note summary returned by {@link XhsNoteSearchPort}.
 */
public final class XhsNoteSearchHit {

    private final String noteId;
    private final String title;
    private final String desc;
    private final String noteUrl;
    private final String likedCount;
    private final String author;

    public XhsNoteSearchHit(String noteId,
                            String title,
                            String desc,
                            String noteUrl,
                            String likedCount,
                            String author) {
        this.noteId = noteId;
        this.title = title;
        this.desc = desc;
        this.noteUrl = noteUrl;
        this.likedCount = likedCount;
        this.author = author;
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
}
