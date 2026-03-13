# Persistence Reference

## MongoDB

CoCro uses MongoDB 7 as its primary persistence store. Collections:

| Collection | Document type | Domain aggregate |
|------------|--------------|-----------------|
| `grids` | `GridDocument` | `Grid` |
| `grids` | `SessionDocument` | `Session` |
| `users` | `UserDocument` | `User` |

Note: `Session` documents are stored in the `grids` collection (historical naming — the collection predates the session concept being split out). The `@Document(collection = "grids")` annotation appears on both `GridDocument` and `SessionDocument`; they are distinguished by a `type` discriminator field.

## Document → Domain Mapping Pattern

Every aggregate follows the same three-class structure:

### 1. `XxxDocument` — the MongoDB record

```kotlin
@Document(collection = "grids")
data class SessionDocument(
    @Id val id: UUID,
    val type: String = "SESSION",
    val shareCode: String,
    val status: String,
    val creatorId: String,
    val participants: List<ParticipantDocument>,
    val gridShareCode: String,
    val sessionGridState: SessionGridStateDocument?,
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
        status = SessionStatus.valueOf(status),
        // ...
    )

    fun Session.toDocument(): SessionDocument = SessionDocument(
        id = id.value,
        shareCode = shareCode.value,
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
        springRepo.findByShareCodeAndType(code.value, "SESSION")
            ?.toDomain()

    override fun save(session: Session) {
        springRepo.save(session.toDocument())
    }
}
```

### 4. `SpringDataXxxRepository` — Spring Data interface

```kotlin
interface SpringDataSessionRepository : MongoRepository<SessionDocument, UUID> {
    fun findByShareCodeAndType(shareCode: String, type: String): SessionDocument?
}
```

## SessionGridStateDocument

Grid state cells are stored flattened as `List<CellDocument>` (not a `Map`). The `toDomain()` mapper reconstructs `Map<CellPos, SessionGridCellState>` on read.

```kotlin
data class SessionGridStateDocument(
    val gridShareCode: String,
    val revision: Long,
    val cells: List<CellDocument>,
)

data class CellDocument(
    val x: Int,
    val y: Int,
    val letter: String?,
    val lockedBy: String?,
)
```

Reconstruction on read:

```kotlin
fun SessionGridStateDocument.toDomain(): SessionGridState = SessionGridState(
    gridShareCode = GridShareCode(gridShareCode),
    revision = revision,
    cells = cells.associate { cell ->
        CellPos(cell.x, cell.y) to SessionGridCellState(
            letter = cell.letter?.firstOrNull()?.let { Letter(it) },
            lockedBy = cell.lockedBy?.let { UserId(it) },
        )
    },
)
```

## Partial Updates

`SessionRepository.updateGridState(sessionId, gridState)` uses `mongoTemplate.updateFirst()` with a targeted `Update` — avoids rewriting the full `SessionDocument` on every grid cell change.

```kotlin
override fun updateGridState(sessionId: SessionId, gridState: SessionGridState) {
    mongoTemplate.updateFirst(
        Query(Criteria.where("_id").`is`(sessionId.value).and("type").`is`("SESSION")),
        Update.update("sessionGridState", gridState.toDocument())
            .set("updatedAt", Instant.now()),
        SessionDocument::class.java,
    )
}
```

This is called by the flush scheduler and by use cases that need to persist an updated grid state without touching participant lists or session status.

## GridDocument

`Grid` cells are stored as `List<CellDocument>` with a `type` string discriminator:

| `type` value | Domain class |
|-------------|-------------|
| `LETTER` | `Cell.LetterCell` |
| `CLUE_SINGLE` | `Cell.ClueCell` (single direction) |
| `CLUE_DOUBLE` | `Cell.ClueCell` (both directions) |
| `BLACK` | `Cell.BlackCell` |

The `GridDocumentMapper` uses a `when (cell.type)` branch to reconstruct the correct sealed subtype on read.

## Infrastructure File Layout

```
infrastructure/persistence/mongo/
  document/
    GridDocument.kt
    SessionDocument.kt
    UserDocument.kt
    CellDocument.kt
    SessionGridStateDocument.kt
    ParticipantDocument.kt
  mapper/
    GridDocumentMapper.kt
    SessionDocumentMapper.kt
    UserDocumentMapper.kt
  repository/
    MongoGridRepositoryAdapter.kt
    MongoSessionRepositoryAdapter.kt
    MongoUserRepositoryAdapter.kt
    SpringDataGridRepository.kt
    SpringDataSessionRepository.kt
    SpringDataUserRepository.kt
```
