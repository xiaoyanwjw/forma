# Forma Chunking RFC — Long Fixture for Tech Digest

This document is a synthetic technical RFC used only for unit and integration tests of excerpt chunking. It repeats stable paragraphs so file size exceeds chunking thresholds.

## 1. Scope and Motivation

The tech digest pipeline must split long technical articles into multiple chunks before per-chunk quote extraction. This fixture guarantees at least three logical sections and one fenced code block.

## 2. Terminology

- **Chunk**: A contiguous substring of the source markdown with metadata.

- **Excerpt**: A heading plus an array of verbatim quotes validated against chunk text.

- **WCC**: Web Content Crawler dataset item shape used in fetch tests.

## 3. Reference Implementation Sketch

The following pseudocode illustrates how a guard might split on headings:

```go
func SplitByHeading(md string, maxRunes int) []string {
    parts := []string{}
    // split on lines starting with "## "
    for _, block := range blocks {
        if utf8.RuneCountInString(block) > maxRunes {
            parts = append(parts, hardSplit(block, maxRunes)...)
        } else {
            parts = append(parts, block)
        }
    }
    return parts
}
```

## 4. Operational Requirements

## 5. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 5: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 6. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 6: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 7. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 7: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 8. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 8: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 9. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 9: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 10. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 10: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 11. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 11: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 12. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 12: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 13. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 13: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 14. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 14: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 15. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 15: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 16. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 16: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 17. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 17: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 18. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 18: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 19. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 19: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 20. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 20: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 21. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 21: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 22. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 22: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 23. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 23: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 24. Repeated Reliability Notes

Operators MUST preserve UTF-8 encoding end to end. When partial coverage occurs because the document exceeds model context, uncertainties MUST mention which sections were not excerpted. Quotes MUST be substring-checked against the chunk that produced them; quotes failing validation are dropped rather than rewritten. The main summarization model MUST NOT receive full chunk bodies—only structured excerpts JSON. 
Additional detail for section 24: latency budgets, retry policies, and idempotent tool calls ensure that re-running excerpt_chunks on the same source.md yields stable headings. Tests assert a minimum byte size so that hard-split paths activate in CI.

## 25. Security Considerations

This fixture contains no secrets. Production fetch tools must not log cookies or authorization headers.

## 26. IANA Considerations

None.
