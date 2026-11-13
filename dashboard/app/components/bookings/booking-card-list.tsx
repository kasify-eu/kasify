import { IconCalendar, IconUserCheck, IconUsers } from "@tabler/icons-react";
import type { Booking } from "~/types/bookings";
import { useTranslation } from "react-i18next";
import {
  ChannelBadge,
  formatDateRange,
  formatGuestCount,
  getGuestName,
} from "./booking-utils";

interface BookingCardListProps {
  bookings: Booking[];
  onBookingClick: (bookingId: string) => void;
  showPropertyName?: boolean;
}

export function BookingCardList({
  bookings,
  onBookingClick,
  showPropertyName = true,
}: BookingCardListProps) {
  const { t } = useTranslation();
  if (bookings.length === 0) {
    return (
      <p className="py-12 text-center text-sm text-slate-400">
        {t("bookings.noResultsFiltered")}
      </p>
    );
  }

  return (
    <div className="space-y-3">
      {bookings.map((booking) => {
        const { dateRange, nights } = formatDateRange(booking);
        const today = new Date().toISOString().split("T")[0];
        const isActive = booking.from <= today && today <= booking.to;
        return (
          <div
            key={booking.id}
            role="button"
            tabIndex={0}
            onClick={() => onBookingClick(booking.id)}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                onBookingClick(booking.id);
              }
            }}
            className="bg-white rounded-xl p-4 shadow-sm border border-slate-100 cursor-pointer active:bg-slate-50"
          >
            <div className="flex items-start justify-between">
              <div>
                <p className="text-base font-bold text-slate-900 flex items-center gap-1.5">
                  {isActive && (
                    <span className="inline-block w-2 h-2 rounded-full bg-green-500 shrink-0" />
                  )}
                  {getGuestName(booking)}
                </p>
                {showPropertyName && (
                  <p className="text-xs font-medium text-slate-500 mt-0.5">
                    {booking.property.name || "Unknown Property"}
                  </p>
                )}
              </div>
              <div className="flex items-center gap-2">
                <div className="inline-flex items-center px-2 py-1 rounded-lg border border-slate-200 bg-white">
                  <IconUserCheck
                    size={16}
                    className={booking.checkin_completed_at ? "text-green-600" : "text-slate-200"}
                  />
                </div>
                <ChannelBadge channel={booking.channel} />
              </div>
            </div>
            <div className="mt-3 space-y-1.5">
              <div className="flex items-center gap-2 text-sm text-slate-600">
                <IconCalendar size={16} className="text-slate-400" />
                <span>
                  {dateRange} · {nights}
                </span>
              </div>
              <div className="flex items-center gap-2 text-sm text-slate-600">
                <IconUsers size={16} className="text-slate-400" />
                <span>{formatGuestCount(booking)}</span>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
