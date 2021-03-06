/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager;

import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.SpecialPriceRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static alfio.manager.testSupport.TicketCategoryGenerator.generateCategoryStream;
import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Mockito.*;

@RunWith(JunitSuiteRunner.class)
public class EventManagerTest {{

    final int hundred = 10000;//100.00
    describe("evaluatePrice", it -> {
        it.should("deduct vat if included into event price", expect -> expect.that(EventManager.evaluatePrice(hundred, BigDecimal.TEN, true, false)).is(9091));
        it.should("not deduct vat if not included into event price", expect -> expect.that(EventManager.evaluatePrice(hundred, BigDecimal.TEN, false, false)).is(hundred));
        it.should("return BigDecimal.ZERO if the event is free of charge", expect -> expect.that(EventManager.evaluatePrice(hundred, BigDecimal.TEN, false, true)).is(0));
    });

    describe("handleTicketNumberModification", it -> {
        TicketCategory original = Mockito.mock(TicketCategory.class);
        TicketCategory updated = Mockito.mock(TicketCategory.class);
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        NamedParameterJdbcTemplate jdbc = it.usesMock(NamedParameterJdbcTemplate.class);
        EventManager eventManager = new EventManager(null, null, null, ticketRepository, null, null, null, null, jdbc, null);
        when(original.getId()).thenReturn(20);
        when(updated.getId()).thenReturn(30);
        when(original.getPriceInCents()).thenReturn(1000);
        when(updated.getPriceInCents()).thenReturn(1000);
        when(original.getMaxTickets()).thenReturn(10);
        when(updated.getMaxTickets()).thenReturn(11);
        it.should("throw exception if there are tickets already sold", expect -> {
            when(ticketRepository.lockTicketsToInvalidate(10, 30, 2)).thenReturn(Collections.singletonList(1));
            expect.exception(IllegalStateException.class, () -> eventManager.handleTicketNumberModification(10, original, updated, -2));
            verify(ticketRepository, never()).invalidateTickets(anyListOf(Integer.class));
        });
        it.should("invalidate exceeding tickets", expect -> {
            final List<Integer> ids = Arrays.asList(1, 2);
            when(ticketRepository.lockTicketsToInvalidate(10, 30, 2)).thenReturn(ids);
            eventManager.handleTicketNumberModification(10, original, updated, -2);
            verify(ticketRepository, times(1)).invalidateTickets(ids);
        });
        it.should("do nothing if the difference is zero", expect -> {
            eventManager.handleTicketNumberModification(10, original, updated, 0);
            verify(ticketRepository, never()).invalidateTickets(anyListOf(Integer.class));
            verify(jdbc, never()).batchUpdate(anyString(), any(SqlParameterSource[].class));
        });

        it.should("insert a new Ticket if the difference is 1", expect -> {
            eventManager.handleTicketNumberModification(10, original, updated, 1);
            verify(ticketRepository, never()).invalidateTickets(anyListOf(Integer.class));
            ArgumentCaptor<SqlParameterSource[]> captor = ArgumentCaptor.forClass(SqlParameterSource[].class);
            verify(jdbc, times(1)).batchUpdate(anyString(), captor.capture());
            expect.that(captor.getValue().length).is(1);
        });
    });

    describe("handlePriceChange", it -> {
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        EventManager eventManager = new EventManager(null, null, null, ticketRepository, null, null, null, null, null, null);
        TicketCategory original = Mockito.mock(TicketCategory.class);
        TicketCategory updated = Mockito.mock(TicketCategory.class);

        it.should("do nothing if the price is not changed", expect -> {
            when(original.getPriceInCents()).thenReturn(10);
            when(updated.getPriceInCents()).thenReturn(10);
            eventManager.handlePriceChange(10, original, updated);
            verify(ticketRepository, never()).selectTicketInCategoryForUpdate(anyInt(), anyInt(), anyInt());
            verify(ticketRepository, never()).updateTicketPrice(anyInt(), anyInt(), anyInt());
        });

        it.should("throw an exception if there aren't enough tickets", expect -> {
            when(original.getPriceInCents()).thenReturn(10);
            when(updated.getPriceInCents()).thenReturn(11);
            when(updated.getMaxTickets()).thenReturn(2);
            when(updated.getId()).thenReturn(20);
            when(ticketRepository.selectTicketInCategoryForUpdate(eq(10), eq(20), eq(2))).thenReturn(Collections.singletonList(1));
            expect.exception(IllegalStateException.class, () -> eventManager.handlePriceChange(10, original, updated));
            verify(ticketRepository, never()).updateTicketPrice(anyInt(), anyInt(), anyInt());
        });

        it.should("update tickets if constraints are verified", expect -> {
            when(original.getPriceInCents()).thenReturn(10);
            when(updated.getPriceInCents()).thenReturn(11);
            when(updated.getMaxTickets()).thenReturn(2);
            when(updated.getId()).thenReturn(20);
            when(ticketRepository.selectTicketInCategoryForUpdate(eq(10), eq(20), eq(2))).thenReturn(Arrays.asList(1, 2));
            eventManager.handlePriceChange(10, original, updated);
            verify(ticketRepository, times(1)).updateTicketPrice(20, 10, 11);
        });
    });

    describe("handleTokenModification", it -> {
        SpecialPriceRepository specialPriceRepository = it.usesMock(SpecialPriceRepository.class);
        NamedParameterJdbcTemplate jdbc = it.usesMock(NamedParameterJdbcTemplate.class);
        EventManager eventManager = new EventManager(null, null, null, null, null, specialPriceRepository, null, null, jdbc, null);
        TicketCategory original = Mockito.mock(TicketCategory.class);
        TicketCategory updated = Mockito.mock(TicketCategory.class);

        it.should("do nothing if both categories are not 'access restricted'", expect -> {
            when(original.isAccessRestricted()).thenReturn(false);
            when(updated.isAccessRestricted()).thenReturn(false);
            eventManager.handleTokenModification(original, updated, 50);
            verify(jdbc, never()).batchUpdate(anyString(), any(SqlParameterSource[].class));
        });

        it.should("handle the activation of access restriction", expect -> {
            when(original.isAccessRestricted()).thenReturn(false);
            when(updated.isAccessRestricted()).thenReturn(true);
            when(updated.getMaxTickets()).thenReturn(50);
            eventManager.handleTokenModification(original, updated, 50);
            ArgumentCaptor<SqlParameterSource[]> captor = ArgumentCaptor.forClass(SqlParameterSource[].class);
            verify(jdbc, times(1)).batchUpdate(anyString(), captor.capture());
            expect.that(captor.getValue().length).is(50);
        });

        it.should("handle the deactivation of access restriction", expect -> {
            when(original.isAccessRestricted()).thenReturn(true);
            when(updated.isAccessRestricted()).thenReturn(false);
            when(updated.getId()).thenReturn(20);
            eventManager.handleTokenModification(original, updated, 50);
            verify(specialPriceRepository, times(1)).cancelExpiredTokens(eq(20));
        });

        it.should("handle the ticket addition", expect -> {
            when(original.isAccessRestricted()).thenReturn(true);
            when(updated.isAccessRestricted()).thenReturn(true);
            eventManager.handleTokenModification(original, updated, 50);
            ArgumentCaptor<SqlParameterSource[]> captor = ArgumentCaptor.forClass(SqlParameterSource[].class);
            verify(jdbc, times(1)).batchUpdate(anyString(), captor.capture());
            expect.that(captor.getValue().length).is(50);
        });

        it.should("handle the ticket removal", expect -> {
            when(original.isAccessRestricted()).thenReturn(true);
            when(updated.isAccessRestricted()).thenReturn(true);
            when(updated.getId()).thenReturn(20);
            final List<Integer> ids = Arrays.asList(1, 2);
            when(specialPriceRepository.lockTokens(eq(20), eq(2))).thenReturn(ids);
            eventManager.handleTokenModification(original, updated, -2);
            verify(specialPriceRepository, times(1)).cancelTokens(ids);
        });

        it.should("fail if there are not enough tickets", expect -> {
            when(original.isAccessRestricted()).thenReturn(true);
            when(updated.isAccessRestricted()).thenReturn(true);
            when(updated.getId()).thenReturn(20);
            final List<Integer> ids = Collections.singletonList(1);
            when(specialPriceRepository.lockTokens(eq(20), eq(2))).thenReturn(ids);
            expect.exception(IllegalArgumentException.class, () -> eventManager.handleTokenModification(original, updated, -2));
            verify(specialPriceRepository, never()).cancelTokens(anyListOf(Integer.class));
        });
    });

    describe("unbounded categories handling", it -> {
        int eventId = 0;
        TicketCategoryRepository ticketCategoryRepository = it.usesMock(TicketCategoryRepository.class);
        EventManager eventManager = new EventManager(null, null, ticketCategoryRepository, null, null, null, null, null, null, null);
        Event event = Mockito.mock(Event.class);
        int availableSeats = 20;
        when(event.getAvailableSeats()).thenReturn(availableSeats);
        it.should("create tickets for the unbounded category", expect -> {
            List<TicketCategory> categories = generateCategoryStream().limit(3).collect(Collectors.toList());
            when(ticketCategoryRepository.findByEventId(eq(eventId))).thenReturn(categories);
            MapSqlParameterSource[] parameterSources = eventManager.prepareTicketsBulkInsertParameters(eventId, ZonedDateTime.now(), event, 1000);
            expect.that(parameterSources).isNotNull();
            expect.that(parameterSources.length).is(availableSeats);
        });

        it.should("create tickets for the unbounded categories", expect -> {
            List<TicketCategory> categories = generateCategoryStream().limit(6).collect(Collectors.toList());
            when(ticketCategoryRepository.findByEventId(eq(eventId))).thenReturn(categories);
            MapSqlParameterSource[] parameterSources = eventManager.prepareTicketsBulkInsertParameters(eventId, ZonedDateTime.now(), event, 1000);
            expect.that(parameterSources).isNotNull();
            expect.that(parameterSources.length).is(availableSeats);
        });

        it.should("create tickets only for the bounded categories", expect -> {
            List<TicketCategory> categories = generateCategoryStream().limit(2).collect(Collectors.toList());
            when(ticketCategoryRepository.findByEventId(eq(eventId))).thenReturn(categories);
            MapSqlParameterSource[] parameterSources = eventManager.prepareTicketsBulkInsertParameters(eventId, ZonedDateTime.now(), event, 1000);
            expect.that(parameterSources).isNotNull();
            expect.that(parameterSources.length).is(4);
        });

    });

    describe("unbind tickets from category", it -> {
        int eventId = 0;
        String eventName = "myEvent";
        String username = "username";
        int categoryId = 1;
        int organizationId = 2;
        TicketCategoryRepository ticketCategoryRepository = it.usesMock(TicketCategoryRepository.class);
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        EventRepository eventRepository = it.usesMock(EventRepository.class);
        UserManager userManager = it.usesMock(UserManager.class);
        TicketReservationManager ticketReservationManager = mock(TicketReservationManager.class);
        when(ticketReservationManager.loadModifiedTickets(eq(eventId), eq(categoryId))).thenReturn(Collections.emptyList());
        SpecialPriceRepository specialPriceRepository = mock(SpecialPriceRepository.class);
        when(specialPriceRepository.findAllByCategoryId(eq(categoryId))).thenReturn(Collections.emptyList());
        EventManager eventManager = new EventManager(userManager, eventRepository, ticketCategoryRepository, ticketRepository, ticketReservationManager, specialPriceRepository, null, null, null, null);
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.getOrganizationId()).thenReturn(organizationId);
        Organization organization = mock(Organization.class);
        when(organization.getId()).thenReturn(organizationId);
        TicketCategory ticketCategory = it.usesMock(TicketCategory.class);

        it.isSetupWith(() -> {
            when(eventRepository.findByShortName(eq(eventName))).thenReturn(event);
            when(ticketCategory.getId()).thenReturn(categoryId);
            when(ticketCategoryRepository.getById(eq(categoryId), eq(eventId))).thenReturn(ticketCategory);
        });

        it.should("not unbind from an event which doesn't contain unbounded categories", expect -> {
            when(ticketCategoryRepository.countUnboundedCategoriesByEventId(eq(eventId))).thenReturn(0);
            when(userManager.findUserOrganizations(eq(username))).thenReturn(Collections.singletonList(organization));
            expect.exception(IllegalArgumentException.class, () -> eventManager.unbindTickets(eventName, categoryId, username));
            verify(ticketCategoryRepository).countUnboundedCategoriesByEventId(eq(eventId));
            verify(userManager).findUserOrganizations(eq(username));
            verify(eventRepository).findByShortName(eq(eventName));
            verifyNoMoreInteractions(ticketCategoryRepository, userManager, eventRepository, ticketRepository);
        });

        it.should("not unbind from a category which is not bounded", expect -> {
            when(ticketCategoryRepository.countUnboundedCategoriesByEventId(eq(eventId))).thenReturn(1);
            when(userManager.findUserOrganizations(eq(username))).thenReturn(Collections.singletonList(organization));
            when(ticketCategory.isBounded()).thenReturn(false);
            expect.exception(IllegalArgumentException.class, () -> eventManager.unbindTickets(eventName, categoryId, username));
            verify(ticketCategoryRepository).countUnboundedCategoriesByEventId(eq(eventId));
            verify(ticketCategoryRepository).getById(eq(categoryId), eq(eventId));
            verify(userManager).findUserOrganizations(eq(username));
            verify(eventRepository).findByShortName(eq(eventName));
            verifyNoMoreInteractions(ticketCategoryRepository, userManager, eventRepository, ticketRepository);
        });

        it.should("unbind tickets from a bounded category", expect -> {
            when(ticketCategoryRepository.countUnboundedCategoriesByEventId(eq(eventId))).thenReturn(1);
            when(userManager.findUserOrganizations(eq(username))).thenReturn(Collections.singletonList(organization));
            when(ticketCategory.isBounded()).thenReturn(true);
            int notSold = 2;
            when(ticketCategory.getMaxTickets()).thenReturn(notSold);
            List<Integer> lockedTickets = Arrays.asList(1, 2);
            when(ticketRepository.selectTicketInCategoryForUpdate(eq(eventId), eq(categoryId), eq(notSold))).thenReturn(lockedTickets);
            when(ticketRepository.unbindTicketsFromCategory(eq(eventId), eq(categoryId), eq(lockedTickets))).thenReturn(notSold);

            eventManager.unbindTickets(eventName, categoryId, username);

            verify(ticketCategoryRepository).countUnboundedCategoriesByEventId(eq(eventId));
            verify(ticketCategoryRepository).getById(eq(categoryId), eq(eventId));
            verify(userManager).findUserOrganizations(eq(username));
            verify(eventRepository).findByShortName(eq(eventName));
            verify(ticketRepository).selectTicketInCategoryForUpdate(eq(eventId), eq(categoryId), eq(notSold));
            verify(ticketRepository).unbindTicketsFromCategory(eq(eventId), eq(categoryId), eq(lockedTickets));
            verify(ticketCategoryRepository).updateSeatsAvailability(eq(categoryId), eq(0));
            verifyNoMoreInteractions(ticketCategoryRepository, userManager, eventRepository, ticketRepository);
        });

    });



}}