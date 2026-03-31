package com.cocro.application.grid.usecase

import com.cocro.application.grid.port.GridRepository
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.CocroResult
import com.cocro.domain.grid.error.GridError
import com.cocro.domain.common.model.Author
import com.cocro.domain.grid.model.Grid
import com.cocro.domain.grid.model.GridMetadata
import com.cocro.domain.grid.model.valueobject.GridDimension
import com.cocro.domain.grid.model.valueobject.GridHeight
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.grid.model.valueobject.GridTitle
import com.cocro.domain.grid.model.valueobject.GridWidth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class GetGridUseCaseTest {

    private val gridRepository: GridRepository = mock()
    private val useCase = GetGridUseCase(gridRepository)

    private fun validGrid(shortId: String) = Grid(
        id = UUID.randomUUID(),
        shortId = GridShareCode(shortId),
        metadata = GridMetadata(title = GridTitle("Test Grid"), author = Author(id = UserId.new(), username = "Test"), reference = null, description = null, difficulty = "NONE"),
        dimension = GridDimension(width = GridWidth(5), height = GridHeight(5)),
        cells = emptyList(),
    )

    @Test
    fun `should return full grid dto for valid shortId`() {
        val shortId = "ABCDE1"
        whenever(gridRepository.findByShortId(GridShareCode(shortId))).thenReturn(validGrid(shortId))

        val result = useCase.execute(shortId)

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val dto = (result as CocroResult.Success).value
        assertThat(dto.gridId).isEqualTo(shortId)
    }

    @Test
    fun `should return GridNotFound when grid does not exist`() {
        val shortId = "ZZZZZ1"
        whenever(gridRepository.findByShortId(GridShareCode(shortId))).thenReturn(null)

        val result = useCase.execute(shortId)

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.GridNotFound }
    }

    @Test
    fun `should return InvalidGridId for shortId that fails validation`() {
        val result = useCase.execute("!NVAL")

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.InvalidGridId }
    }

    @Test
    fun `should return InvalidGridId for empty shortId`() {
        val result = useCase.execute("")

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.InvalidGridId }
    }
}
