package fr.geoffreyCoulaud.pinryReborn.api.worker

import fr.geoffreyCoulaud.pinryReborn.api.usecases.DownloadPinMedia
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.PinDownloadTask
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.TaskContext
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PinDownloadTaskHandlerTest {
    private val downloadPinMedia: DownloadPinMedia = mockk(relaxed = true)
    private val handler = PinDownloadTaskHandler(downloadPinMedia)

    @Test
    fun `Given the handler, Then its kind is pin download`() {
        assertEquals(PinDownloadTask.KIND, handler.kind)
    }

    @Test
    fun `Given a pinId payload, Then it delegates the download of that pin`() {
        val pinId = randomUUID()
        handler.handle(pinId.toString(), TaskContext(1, 5))
        verify { downloadPinMedia.download(pinId, TaskContext(1, 5)) }
    }
}
