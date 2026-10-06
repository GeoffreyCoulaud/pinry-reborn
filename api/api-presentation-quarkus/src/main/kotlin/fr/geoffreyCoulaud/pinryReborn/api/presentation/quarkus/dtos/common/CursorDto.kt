package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.common

import java.util.UUID
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

/**
 * The decoded form of a cursor, opaque on the wire. SmallRye knows neither `Base64Json` nor `Base64JsonSerializer`, so
 * without the type below it would publish the object it decodes to.
 */
@Schema(
    type = SchemaType.STRING,
    description = "Opaque pagination cursor. Read it from a response and send it back unchanged.",
)
class CursorDto(
    val pivotId: UUID,
    val direction: CursorDirectionDto,
)
