import { useCallback, useEffect, useMemo, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import { ApiClient, createApiConfig, handleApiError } from "~/lib/api-client";
import type { Booking } from "~/types/bookings";
import type { PropertyMember, SmartLockInfo } from "~/types/properties";
import { PropertyAccessLevel } from "~/types/properties";

export interface BookingDetailsState {
  booking: Booking | null;
  setBooking: Dispatch<SetStateAction<Booking | null>>;
  loading: boolean;
  error: string | null;
  clearError: () => void;
  team: PropertyMember[];
  smartLocks: SmartLockInfo[];
  propertyRole: PropertyAccessLevel | null;
  defaultCleanerId: string | null;
  refetch: () => Promise<void>;
}

export function useBookingDetails(
  bookingId: string | null,
  opened: boolean,
  admin = false,
): BookingDetailsState {
  const apiClient = useMemo(
    () => ApiClient.createFromBrowser(createApiConfig()),
    [],
  );

  const [booking, setBooking] = useState<Booking | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [team, setTeam] = useState<PropertyMember[]>([]);
  const [smartLocks, setSmartLocks] = useState<SmartLockInfo[]>([]);
  const [propertyRole, setPropertyRole] = useState<PropertyAccessLevel | null>(
    null,
  );
  const [defaultCleanerId, setDefaultCleanerId] = useState<string | null>(null);

  const fetchBooking = useCallback(
    async (id: string, signal: AbortSignal) => {
      setLoading(true);
      setError(null);
      try {
        const response = admin
          ? await apiClient.getAdminBooking(id)
          : await apiClient.getBooking(id);
        if (signal.aborted) return;
        setBooking(response.data);
      } catch (err) {
        if (signal.aborted) return;
        setError(handleApiError(err).message);
      } finally {
        if (!signal.aborted) setLoading(false);
      }
    },
    [apiClient, admin],
  );

  useEffect(() => {
    if (!bookingId || !opened) return;
    const controller = new AbortController();
    fetchBooking(bookingId, controller.signal);
    return () => controller.abort();
  }, [bookingId, opened, fetchBooking]);

  // Admins are platform staff, not property crew, so the property-scoped
  // side-load (team/smart locks/role) would 401/403. Skipping it also keeps the
  // admin drawer cleanly read-only: an empty role hides the edit/access-code
  // sections that hit owner-only endpoints.
  const propertyId = admin ? null : (booking?.property.id ?? null);
  useEffect(() => {
    if (!propertyId) return;
    let cancelled = false;
    apiClient
      .getProperty(propertyId)
      .then((response) => {
        if (cancelled) return;
        setTeam(response.data.team ?? []);
        setSmartLocks(response.data.smart_locks ?? []);
        setPropertyRole(response.data.role ?? null);
        setDefaultCleanerId(response.data.default_cleaner?.id ?? null);
      })
      .catch(() => {
        /* silent — property side-load is best-effort */
      });
    return () => {
      cancelled = true;
    };
  }, [propertyId, apiClient]);

  useEffect(() => {
    if (!opened) setError(null);
  }, [opened]);

  const refetch = useCallback(async () => {
    if (!bookingId) return;
    const controller = new AbortController();
    await fetchBooking(bookingId, controller.signal);
  }, [bookingId, fetchBooking]);

  return {
    booking,
    setBooking,
    loading,
    error,
    clearError: () => setError(null),
    team,
    smartLocks,
    propertyRole,
    defaultCleanerId,
    refetch,
  };
}
