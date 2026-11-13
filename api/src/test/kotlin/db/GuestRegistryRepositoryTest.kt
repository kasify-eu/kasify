package eu.kasify.db

import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.SesCodigoEstado
import eu.kasify.model.GuestRegistrySnapshot
import eu.kasify.model.SesCommunicationType
import eu.kasify.routes.BaseRouteDBTests
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class GuestRegistryRepositoryTest : BaseRouteDBTests() {
    private val repository = GuestRegistryRepository()

    private val bookingA = Uuid.parse("40000000-0000-0000-0000-0000000000b1")
    private val bookingB = Uuid.parse("40000000-0000-0000-0000-0000000000b2")
    private val otherPropertyId = Uuid.parse("30000000-0000-0000-0000-0000000000b9")

    // guest_registry_entries has no foreign keys by design (entries must survive parent
    // deletes), so tests can reference bookings/properties that were never inserted.
    override fun setupData() {}

    private fun newEntry(
        bookingId: Uuid,
        propertyId: Uuid = TEST_ACCOUNT_1_PROPERTY_1.id,
        type: SesCommunicationType = SesCommunicationType.PV,
        checkinDate: LocalDate = LocalDate.of(2026, 5, 20),
        sesLote: String = "lote-1",
    ) = NewGuestRegistryEntry(
        accountId = TEST_ACCOUNT_1_PROPERTY_1.accountId,
        propertyId = propertyId,
        bookingId = bookingId,
        communicationType = type,
        sesArrendadorCode = "0000000123",
        arrendadorNif = "12345678Z",
        sesEstablishmentCode = "H1234AB",
        propertyName = "La casita del test",
        propertyAddress = "Calle Mayor 1",
        postalCode = "28001",
        city = "Madrid",
        province = "Madrid",
        contractReference = "ref-$bookingId",
        contractDate = LocalDate.of(2026, 5, 1),
        bookingChannel = BookingChannel.Direct,
        checkinDate = checkinDate,
        checkoutDate = checkinDate.plusDays(5),
        numGuests = 2,
        snapshot =
            GuestRegistrySnapshot(
                contract =
                    GuestRegistrySnapshot.Contract(
                        referencia = "ref-$bookingId",
                        fechaContrato = "2026-05-01",
                        fechaEntrada = "2026-05-20T15:00:00",
                        fechaSalida = "2026-05-25T11:00:00",
                        numPersonas = 2,
                        tipoPago = "PLATF",
                    ),
                guests =
                    listOf(
                        GuestRegistrySnapshot.Person(
                            rol = "VI",
                            nombre = "Ana",
                            apellido1 = "García",
                            fechaNacimiento = "1990-01-01",
                        ),
                    ),
            ),
        requestXml = "<alt:peticion/>",
        sesLote = sesLote,
        codigoComunicacion = null,
        submittedAt = OffsetDateTime.now(),
    )

    @Test
    fun `record assigns correlative entry numbers per property`() =
        runBlocking {
            assertNotNull(repository.record(newEntry(bookingA)))
            assertNotNull(repository.record(newEntry(bookingB)))
            val otherPropertyBooking = Uuid.parse("40000000-0000-0000-0000-0000000000b3")
            assertNotNull(repository.record(newEntry(otherPropertyBooking, propertyId = otherPropertyId)))

            val entries = repository.listByProperty(TEST_ACCOUNT_1_PROPERTY_1.id)
            assertEquals(listOf(1L, 2L), entries.map { it.entryNumber })

            val otherEntries = repository.listByProperty(otherPropertyId)
            assertEquals(listOf(1L), otherEntries.map { it.entryNumber })
        }

    @Test
    fun `record is idempotent for a retried persistence step of the same submission`() =
        runBlocking {
            assertNotNull(repository.record(newEntry(bookingA, sesLote = "lote-1")))
            assertNull(repository.record(newEntry(bookingA, sesLote = "lote-1")))

            val entries = repository.listByProperty(TEST_ACCOUNT_1_PROPERTY_1.id)
            assertEquals(1, entries.size)
        }

    @Test
    fun `a re-filed alta with a new lote appends a new entry`() =
        runBlocking {
            assertNotNull(repository.record(newEntry(bookingA, sesLote = "lote-1")))
            assertNotNull(repository.record(newEntry(bookingA, sesLote = "lote-2")))

            val entries = repository.listByProperty(TEST_ACCOUNT_1_PROPERTY_1.id)
            assertEquals(listOf(1L, 2L), entries.map { it.entryNumber })
            assertEquals(listOf("lote-1", "lote-2"), entries.map { it.sesLote })
        }

    @Test
    fun `the same booking can have one PV and one RH entry`() =
        runBlocking {
            assertNotNull(repository.record(newEntry(bookingA, type = SesCommunicationType.RH)))
            assertNotNull(repository.record(newEntry(bookingA, type = SesCommunicationType.PV)))

            val entries = repository.listByProperty(TEST_ACCOUNT_1_PROPERTY_1.id)
            assertEquals(2, entries.size)
            assertEquals(setOf(SesCommunicationType.PV, SesCommunicationType.RH), entries.map { it.communicationType }.toSet())
        }

    @Test
    fun `updateEstado mirrors the polled estado and late codigo onto the entry`() =
        runBlocking {
            repository.record(newEntry(bookingA))

            val updated =
                repository.updateEstado(
                    bookingId = bookingA,
                    type = SesCommunicationType.PV,
                    sesLote = "lote-1",
                    estado = SesCodigoEstado.TramitadoSinErrores_1,
                    codigoComunicacion = "COM-123",
                )
            assertEquals(true, updated)

            val entry = repository.getByBookingAndType(bookingA, SesCommunicationType.PV)
            assertNotNull(entry)
            assertEquals(SesCodigoEstado.TramitadoSinErrores_1, entry.sesEstado)
            assertEquals("COM-123", entry.codigoComunicacion)
            // The frozen parte itself is untouched.
            assertEquals(
                "Ana",
                entry.snapshot.guests
                    .first()
                    .nombre,
            )
            assertEquals("<alt:peticion/>", entry.requestXml)
        }

    @Test
    fun `updateEstado only touches the entry of the polled submission`() =
        runBlocking {
            repository.record(newEntry(bookingA, sesLote = "lote-1"))
            repository.updateEstado(
                bookingId = bookingA,
                type = SesCommunicationType.PV,
                sesLote = "lote-1",
                estado = SesCodigoEstado.ErrorInesperado_3,
                codigoComunicacion = "COM-1",
            )
            // The corrected alta is re-filed under a new lote and later polled as accepted.
            repository.record(newEntry(bookingA, sesLote = "lote-2"))
            repository.updateEstado(
                bookingId = bookingA,
                type = SesCommunicationType.PV,
                sesLote = "lote-2",
                estado = SesCodigoEstado.TramitadoSinErrores_1,
                codigoComunicacion = "COM-2",
            )

            val entries = repository.listByProperty(TEST_ACCOUNT_1_PROPERTY_1.id)
            assertEquals(2, entries.size)
            val (first, second) = entries
            // The rejected submission's entry stays frozen as rejected under its own código.
            assertEquals(SesCodigoEstado.ErrorInesperado_3, first.sesEstado)
            assertEquals("COM-1", first.codigoComunicacion)
            assertEquals(SesCodigoEstado.TramitadoSinErrores_1, second.sesEstado)
            assertEquals("COM-2", second.codigoComunicacion)
        }

    @Test
    fun `updateEstado without an entry is a no-op`() =
        runBlocking {
            val updated =
                repository.updateEstado(
                    bookingId = bookingA,
                    type = SesCommunicationType.PV,
                    sesLote = "lote-1",
                    estado = SesCodigoEstado.TramitadoSinErrores_1,
                )
            assertEquals(false, updated)
        }

    @Test
    fun `listByProperty filters by checkin date and keeps libro order`() =
        runBlocking {
            repository.record(newEntry(bookingA, checkinDate = LocalDate.of(2026, 5, 20)))
            repository.record(newEntry(bookingB, checkinDate = LocalDate.of(2026, 7, 10)))

            val may = repository.listByProperty(TEST_ACCOUNT_1_PROPERTY_1.id, checkinTo = LocalDate.of(2026, 6, 1))
            assertEquals(listOf(bookingA), may.map { it.bookingId })

            val july =
                repository.listByProperty(
                    TEST_ACCOUNT_1_PROPERTY_1.id,
                    checkinFrom = LocalDate.of(2026, 7, 1),
                    checkinTo = LocalDate.of(2026, 7, 31),
                )
            assertEquals(listOf(bookingB), july.map { it.bookingId })

            val all = repository.listByProperty(TEST_ACCOUNT_1_PROPERTY_1.id)
            assertEquals(listOf(1L, 2L), all.map { it.entryNumber })
        }
}
