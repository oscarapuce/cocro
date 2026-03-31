# Persistence Reference

## MongoDB

CoCro uses MongoDB 7 as its primary persistence store. Collections:

| Collection | Document type | Domain aggregate |
|------------|--------------|-----------------|
| `grids` | `GridDocument` | `Grid` |
| `sessions` | `SessionDocument` | `Session` |
| `users` | `UserDocument` | `User` |

## Document → Domain Mapping Pattern

Every aggregate follows the same three-class structure:

### 1. `XxxDocument` — the MongoDB record

```kotlin
@Document(collection = "sessions")
data class SessionDocument(
    @Id val id: String,
    val shareCode: String,
    val authorId: String,
    val authorUsername: String,
    val creatorId: String? = null,  // legacy fallback field
    val gridShortId: String,
    val gridTemplate: GridTemplateDocument?,
    val status: String,
    val participants: Set<ParticipantDocument>,
    val sessionGridState: SessionGridStateDocument,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

### 2. `XxxDocumentMapper` — pure Kotlin object, no Spring dependencies

```kotlin
object SessionDocumentMapper {
    fun SessionDocument.toDomain(): Session = Session(
        id = SessionId(id),
        shareCode = SessionShareCode(shareCode),
        author = Author(UserId(authorId), authorUsername),
        status = SessionStatus.valueOf(status),
        // ...
    )

    fun Session.toDocument(): SessionDocument = SessionDocument(
        id = id.value,
        shareCode = shareCode.value,
        authorId = author.id.value,
        authorUsername = author.username,
        status = status.name,
        // ...
    )
}
```

### 3. `MongoXxxRepositoryAdapter` — Spring bean, implements the port

```kotlin
@Repository
class MongoSessionRepositoryAdapter(
    private val springRepo: SpringDataSessionRepository,
    private val mongoTemplate: MongoTemplate,
) : SessionRepository {
    override fun findByShareCode(code: SessionShareCode): Session? =
        springRepo.findByShareCode(code.value)
            ?.toDomain()

    override fun save(session: Session) {
        springRepo.save(session.toDocument())
    }
}
```

### 4. `SpringDataXxxRepository` — Spring Data interface

```kotlin
interface SpringDataSessionRepository : MongoRepository<SessionDocument, String> {
    fun findByShareCode(shareCode: String): SessionDocument?
}
```

## SessionGridStateDocument

Grid state cells are stored flattened as `List<CellDocument>` (not a `Map`). The `toDomain()` mapper reconstructs `Map<CellPos, SessionGridCellState>` on read.

```kotlin
data class SessionGridStateDocument(
    val sessionId: String,
    val gridShortId: String,
    val revision: Long,
    val cells: List<CellDocument>,
)
```

## ParticipantDocument

```kotlin
data class ParticipantDocument(
    val userId: String,
    val status: String,   // "JOINED" or "LEFT" — maps to ParticipantStatus enum
)
```

## Partial Updates

`SessionRepository.updateGridState(sessionId, gridState)` uses `mongoTemplate.updateFirst()` with a targeted `Update` — avoids rewriting the full `SessionDocument` on every grid cell change.

```kotlin
override fun updateGridState(sessionId: SessionId, gridState: SessionGridState) {
    mongoTemplate.updateFirst(
        Query(Criteria.where("_id").`is`(sessionId.value)),
        Update.update("sessionGridState", gridState.toDocument())
            .set("updatedAt", Instant.now()),
        SessionDocument::class.java,
    )
}
```

This is called by the flush scheduler and by use cases that need to persist an updated grid state without touching participant lists or session status.

## GridDocument

```kotlin
@Document(collection = "grids")
data class GridDocument(
    @Id val id: String,
    val shortId: String,
    val title: String,
    val metadata: GridMetadataDocument,
    val hashLetters: Long,
    val width: Int,
    val height: Int,
    val cells: List<CellDocument>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

### GridMetadataDocument

```kotlin
data class GridMetadataDocument(
    val authorId: String? = null,
    val authorUsername: String? = null,
    val author: String? = null,  // legacy field (UUID string)
    val reference: String?,
    val description: String?,
    val difficulty: String = "NONE",
    val globalClueLabel: String? = null,
    val globalClueWordLengths: List<Int>? = null,
)
```

### CellDocument

Grid cells are stored as `List<CellDocument>` with a `type` string discriminator:

| `type` value | Domain class |
|-------------|-------------|
| `LETTER` | `Cell.LetterCell` |
| `CLUE_SINGLE` | `Cell.ClueCell` (single direction) |
| `CLUE_DOUBLE` | `Cell.ClueCell` (both directions) |
| `BLACK` | `Cell.BlackCell` |

```kotlin
data class CellDocument(
    val x: Int,
    val y: Int,
    val type: String,
    // Letter
    val letter: Char? = null,
    val separator: String? = null,
    val number: Int? = null,
    // Clues
    val clueDirection: String? = null,
    val clueText: String? = null,
    val secondClueDirection: String? = null,
    val secondClueText: String? = null,
)
```

The `GridDocumentMapper` uses a `when (cell.type)` branch to reconstruct the correct sealed subtype on read.

## Infrastructure File Layout

```
infrastructure/persistence/mongo/
  grid/
    document/
      GridDocument.kt
      GridMetadataDocument.kt
      CellDocument.kt
    mapper/
      GridDocumentMapper.kt
    repository/
      MongoGridRepositoryAdapter.kt
      SpringDataGridRepository.kt
  session/
    document/
      SessionDocument.kt
      SessionGridStateDocument.kt
      ParticipantDocument.kt
      GridTemplateDocument.kt
    mapper/
      SessionDocumentMapper.kt
    repository/
      MongoSessionRepositoryAdapter.kt
      SpringDataSessionRepository.kt
  user/
    document/
      UserDocument.kt
    mapper/
      UserDocumentMapper.kt
    repository/
      MongoUserRepositoryAdapter.kt
      SpringDataUserRepository.kt
```
